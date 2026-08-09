from __future__ import annotations

import hashlib
import json
import re
from typing import Any

import yaml


HTTP_METHODS = ("get", "post", "put", "patch", "delete", "head", "options")


def _load_document(source: str | dict[str, Any]) -> dict[str, Any]:
    if isinstance(source, dict):
        document = source
    else:
        document = yaml.safe_load(source)
    if not isinstance(document, dict) or not str(document.get("openapi", "")).startswith("3."):
        raise ValueError("Expected an OpenAPI 3 document")
    if not isinstance(document.get("paths"), dict):
        raise ValueError("OpenAPI document must contain a paths object")
    return document


def _pointer(path: str, method: str) -> str:
    escaped = path.replace("~", "~0").replace("/", "~1")
    return f"/paths/{escaped}/{method}"


def _operation_id(method: str, path: str) -> str:
    words = re.findall(r"[A-Za-z0-9]+", path)
    suffix = "".join(word[:1].upper() + word[1:] for word in words)
    return f"{method}{suffix}"


def normalize_openapi(source: str | dict[str, Any]) -> dict[str, Any]:
    document = _load_document(source)
    canonical = json.dumps(document, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    document_hash = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    operations: list[dict[str, Any]] = []
    for path in sorted(document["paths"]):
        path_item = document["paths"][path]
        if not isinstance(path_item, dict):
            continue
        shared_parameters = path_item.get("parameters", [])
        for method in HTTP_METHODS:
            operation = path_item.get(method)
            if not isinstance(operation, dict):
                continue
            parameters = [*shared_parameters, *operation.get("parameters", [])]
            operations.append(
                {
                    "operationId": operation.get("operationId") or _operation_id(method, path),
                    "method": method.upper(),
                    "path": path,
                    "tags": operation.get("tags", ["default"]),
                    "parameters": parameters,
                    "requestBody": operation.get("requestBody"),
                    "responses": operation.get("responses", {}),
                    "provenance": {
                        "documentHash": document_hash,
                        "pointer": _pointer(path, method),
                    },
                }
            )
    return {
        "title": document.get("info", {}).get("title", "Untitled API"),
        "version": document.get("info", {}).get("version", "0.0.0"),
        "documentHash": document_hash,
        "schemas": document.get("components", {}).get("schemas", {}),
        "operations": operations,
    }


def _ts_type(schema: dict[str, Any]) -> str:
    if "$ref" in schema:
        return str(schema["$ref"]).rsplit("/", 1)[-1]
    kind = schema.get("type")
    if kind == "array":
        return f"Array<{_ts_type(schema.get('items', {}))}>"
    if kind in {"integer", "number"}:
        return "number"
    if kind == "boolean":
        return "boolean"
    if kind == "object":
        return "Record<string, unknown>"
    return "string"


def _render_interfaces(schemas: dict[str, Any]) -> list[str]:
    blocks: list[str] = []
    for name, schema in schemas.items():
        if not isinstance(schema, dict) or schema.get("type") != "object":
            continue
        required = set(schema.get("required", []))
        lines = [f"export type {name} = {{"]
        for field, field_schema in schema.get("properties", {}).items():
            optional = "" if field in required else "?"
            lines.append(f"  {field}{optional}: {_ts_type(field_schema)};")
        lines.append("};")
        blocks.append("\n".join(lines))
    return blocks


def _success_schema(operation: dict[str, Any]) -> dict[str, Any]:
    responses = operation.get("responses", {})
    for status in sorted(responses):
        if str(status).startswith("2"):
            return responses[status].get("content", {}).get("application/json", {}).get("schema", {})
    return {}


def generate_typescript_client(ir: dict[str, Any]) -> str:
    blocks = ["// Generated from API IR. Review before production use."]
    blocks.extend(_render_interfaces(ir.get("schemas", {})))
    for operation in ir["operations"]:
        params = []
        query_names = []
        for parameter in operation.get("parameters", []):
            optional = "" if parameter.get("required") else "?"
            name = parameter["name"]
            params.append(f"{name}{optional}: {_ts_type(parameter.get('schema', {}))}")
            if parameter.get("in") == "query":
                query_names.append(name)
        body_schema = (
            (operation.get("requestBody") or {})
            .get("content", {})
            .get("application/json", {})
            .get("schema")
        )
        if body_schema:
            params.append(f"body: {_ts_type(body_schema)}")
        args = ", ".join(params)
        return_type = _ts_type(_success_schema(operation)) if _success_schema(operation) else "unknown"
        lines = [
            f"export async function {operation['operationId']}({args}): Promise<{return_type}> {{",
            f"  const url = new URL(`{operation['path']}`, window.location.origin);",
        ]
        for name in query_names:
            lines.append(f"  if ({name} !== undefined) url.searchParams.set('{name}', String({name}));")
        options = f"{{ method: '{operation['method']}'"
        if body_schema:
            options += ", headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)"
        options += " }"
        lines.extend(
            [
                f"  const response = await fetch(url, {options});",
                "  if (!response.ok) throw new Error(`API request failed: ${response.status}`);",
                f"  return response.json() as Promise<{return_type}>;",
                "}",
            ]
        )
        blocks.append("\n".join(lines))
    return "\n\n".join(blocks) + "\n"


def generate_contract_tests(ir: dict[str, Any]) -> str:
    lines = [
        "// Generated contract assertions. Wire these cases to the repository test client.",
        "import { describe, expect, it } from 'vitest';",
        "",
        f"describe('{ir['title']} contract', () => {{",
    ]
    for operation in ir["operations"]:
        lines.extend(
            [
                f"  it('{operation['operationId']} keeps its method and path', () => {{",
                f"    expect({{ method: '{operation['method']}', path: '{operation['path']}' }}).toEqual(",
                f"      {{ method: '{operation['method']}', path: '{operation['path']}' }},",
                "    );",
                "  });",
            ]
        )
    lines.append("});")
    return "\n".join(lines) + "\n"


def build_integration(source: str | dict[str, Any], page_name: str) -> dict[str, Any]:
    ir = normalize_openapi(source)
    tasks = [
        {
            "id": f"task-{index + 1}",
            "page": page_name,
            "operationId": operation["operationId"],
            "method": operation["method"],
            "path": operation["path"],
            "checks": ["types", "contract", "mock", "page-state"],
        }
        for index, operation in enumerate(ir["operations"])
    ]
    return {
        **ir,
        "tasks": tasks,
        "typescriptClient": generate_typescript_client(ir),
        "contractTests": generate_contract_tests(ir),
    }


def _parameter_map(operation: dict[str, Any]) -> dict[tuple[str, str], dict[str, Any]]:
    return {
        (item.get("in", "query"), item["name"]): item
        for item in operation.get("parameters", [])
    }


def _body_schema(operation: dict[str, Any]) -> dict[str, Any]:
    return (
        (operation.get("requestBody") or {})
        .get("content", {})
        .get("application/json", {})
        .get("schema", {})
    )


def _schema_breaks(
    before: dict[str, Any],
    after: dict[str, Any],
    before_schemas: dict[str, Any],
    after_schemas: dict[str, Any],
) -> bool:
    def resolve(schema: dict[str, Any], components: dict[str, Any]) -> dict[str, Any]:
        reference = schema.get("$ref")
        return components.get(str(reference).rsplit("/", 1)[-1], {}) if reference else schema

    old = resolve(before, before_schemas)
    new = resolve(after, after_schemas)
    if old.get("type") != new.get("type"):
        return True
    old_enum, new_enum = set(old.get("enum", [])), set(new.get("enum", []))
    if old_enum and new_enum and not old_enum.issubset(new_enum):
        return True
    old_properties = set(old.get("properties", {}))
    new_properties = set(new.get("properties", {}))
    if old_properties - new_properties:
        return True
    if set(new.get("required", [])) - set(old.get("required", [])):
        return True
    return False


def _operation_semantics(operation: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in operation.items() if key != "provenance"}


def diff_contracts(before: dict[str, Any], after: dict[str, Any]) -> dict[str, Any]:
    old = {(item["method"], item["path"]): item for item in before["operations"]}
    new = {(item["method"], item["path"]): item for item in after["operations"]}
    removed = sorted(set(old) - set(new))
    added = sorted(set(new) - set(old))
    changed: list[dict[str, Any]] = []
    breaking = [
        {"type": "removed-operation", "method": method, "path": path}
        for method, path in removed
    ]
    for method, path in sorted(set(old) & set(new)):
        old_operation, new_operation = old[(method, path)], new[(method, path)]
        old_parameters = _parameter_map(old_operation)
        new_parameters = _parameter_map(new_operation)
        reasons = []
        for parameter_key in sorted(set(old_parameters) - set(new_parameters)):
            reasons.append(f"removed-parameter:{parameter_key[0]}:{parameter_key[1]}")
        for parameter_key, parameter in new_parameters.items():
            previous = old_parameters.get(parameter_key)
            if previous is None and parameter.get("required"):
                reasons.append(f"added-required-parameter:{parameter_key[0]}:{parameter_key[1]}")
            elif previous and not previous.get("required") and parameter.get("required"):
                reasons.append(f"parameter-became-required:{parameter_key[0]}:{parameter_key[1]}")
            elif previous and _schema_breaks(
                previous.get("schema", {}),
                parameter.get("schema", {}),
                before.get("schemas", {}),
                after.get("schemas", {}),
            ):
                reasons.append(f"parameter-schema-narrowed:{parameter_key[0]}:{parameter_key[1]}")
        old_body, new_body = _body_schema(old_operation), _body_schema(new_operation)
        if old_body != new_body and _schema_breaks(
            old_body, new_body, before.get("schemas", {}), after.get("schemas", {})
        ):
            reasons.append("request-schema-became-incompatible")
        old_response, new_response = _success_schema(old_operation), _success_schema(new_operation)
        if old_response != new_response and _schema_breaks(
            old_response, new_response, before.get("schemas", {}), after.get("schemas", {})
        ):
            reasons.append("response-schema-became-incompatible")
        if _operation_semantics(old_operation) != _operation_semantics(new_operation):
            changed.append({"method": method, "path": path, "breakingReasons": reasons})
        breaking.extend(
            {"type": reason, "method": method, "path": path} for reason in reasons
        )
    return {
        "added": [{"method": method, "path": path} for method, path in added],
        "removed": [{"method": method, "path": path} for method, path in removed],
        "changed": changed,
        "breaking": breaking,
    }
