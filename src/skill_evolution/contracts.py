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
    }


def diff_contracts(before: dict[str, Any], after: dict[str, Any]) -> dict[str, Any]:
    old = {(item["method"], item["path"]): item for item in before["operations"]}
    new = {(item["method"], item["path"]): item for item in after["operations"]}
    removed = sorted(set(old) - set(new))
    added = sorted(set(new) - set(old))
    return {
        "added": [{"method": method, "path": path} for method, path in added],
        "removed": [{"method": method, "path": path} for method, path in removed],
        "breaking": [{"type": "removed-operation", "method": method, "path": path} for method, path in removed],
    }
