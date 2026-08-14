from __future__ import annotations

import hashlib
import json
import re
from copy import deepcopy
from typing import Any

import yaml


HTTP_METHODS = ("get", "post", "put", "patch", "delete", "head", "options")
TS_RESERVED = {
    "abstract",
    "any",
    "as",
    "asserts",
    "async",
    "await",
    "boolean",
    "break",
    "case",
    "catch",
    "class",
    "const",
    "constructor",
    "continue",
    "debugger",
    "declare",
    "default",
    "delete",
    "do",
    "else",
    "enum",
    "export",
    "extends",
    "false",
    "finally",
    "for",
    "from",
    "function",
    "get",
    "if",
    "implements",
    "import",
    "in",
    "infer",
    "instanceof",
    "interface",
    "is",
    "keyof",
    "let",
    "module",
    "namespace",
    "never",
    "new",
    "null",
    "number",
    "object",
    "of",
    "package",
    "private",
    "protected",
    "public",
    "readonly",
    "require",
    "return",
    "set",
    "static",
    "string",
    "super",
    "switch",
    "symbol",
    "this",
    "throw",
    "true",
    "try",
    "type",
    "typeof",
    "undefined",
    "unique",
    "unknown",
    "var",
    "void",
    "while",
    "with",
    "yield",
}


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


def _resolve_parameter(document: dict[str, Any], parameter: Any) -> Any:
    if not isinstance(parameter, dict) or "$ref" not in parameter:
        return parameter
    reference = str(parameter["$ref"])
    if not reference.startswith("#/components/parameters/"):
        raise ValueError(f"Only local parameter references are supported: {reference}")
    current: Any = document
    for segment in reference[2:].split("/"):
        key = segment.replace("~1", "/").replace("~0", "~")
        if not isinstance(current, dict) or key not in current:
            raise ValueError(f"Unresolvable parameter reference: {reference}")
        current = current[key]
    if not isinstance(current, dict):
        raise ValueError(f"Parameter reference must resolve to an object: {reference}")
    return deepcopy(current)


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
            parameters = [
                _resolve_parameter(document, parameter)
                for parameter in [*shared_parameters, *operation.get("parameters", [])]
            ]
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


def _ts_type(schema: dict[str, Any], type_names: dict[str, str] | None = None) -> str:
    if "$ref" in schema:
        source_name = str(schema["$ref"]).rsplit("/", 1)[-1]
        return (type_names or {}).get(source_name, _ts_type_name(source_name))
    kind = schema.get("type")
    if kind == "array":
        return f"Array<{_ts_type(schema.get('items', {}), type_names)}>"
    if kind in {"integer", "number"}:
        return "number"
    if kind == "boolean":
        return "boolean"
    if kind == "object":
        return "Record<string, unknown>"
    return "string"


def _ts_identifier(name: str) -> str:
    parts = re.findall(r"[A-Za-z0-9]+", name)
    if not parts:
        raise ValueError(f"Parameter name cannot become a TypeScript identifier: {name}")
    identifier = parts[0][:1].lower() + parts[0][1:]
    identifier += "".join(part[:1].upper() + part[1:] for part in parts[1:])
    if identifier[0].isdigit():
        identifier = f"parameter{identifier}"
    if identifier in TS_RESERVED:
        identifier += "Parameter"
    return identifier


def _ts_type_name(name: str) -> str:
    identifier = _ts_identifier(name)
    return identifier[:1].upper() + identifier[1:]


def _unique_identifiers(values: list[str], *, type_names: bool = False) -> dict[str, str]:
    result: dict[str, str] = {}
    owners: dict[str, str] = {}
    for value in values:
        if value in result:
            raise ValueError(f"Duplicate TypeScript source identifier: {value!r}")
        identifier = _ts_type_name(value) if type_names else _ts_identifier(value)
        previous = owners.get(identifier)
        if previous is not None and previous != value:
            raise ValueError(
                f"TypeScript identifier collision: {previous!r} and {value!r} -> {identifier}"
            )
        owners[identifier] = value
        result[value] = identifier
    return result


def _ts_property_name(name: str) -> str:
    if re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*", name) and name not in TS_RESERVED:
        return name
    return json.dumps(name, ensure_ascii=False)


def _ts_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def _fresh_identifier(base: str, used: set[str]) -> str:
    candidate = base
    suffix = 2
    while candidate in used or candidate in TS_RESERVED:
        candidate = f"{base}{suffix}"
        suffix += 1
    used.add(candidate)
    return candidate


def _parameter_groups(operation: dict[str, Any]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    parameters = operation.get("parameters", [])
    return (
        [parameter for parameter in parameters if parameter.get("required")],
        [parameter for parameter in parameters if not parameter.get("required")],
    )


def _render_interfaces(schemas: dict[str, Any]) -> list[str]:
    blocks: list[str] = []
    type_names = _unique_identifiers(list(schemas), type_names=True)
    for name, schema in schemas.items():
        if not isinstance(schema, dict):
            continue
        type_name = type_names[name]
        enum_values = schema.get("enum")
        if isinstance(enum_values, list):
            values = " | ".join(json.dumps(value, ensure_ascii=False) for value in enum_values)
            blocks.append(f"export type {type_name} = {values};")
            continue
        if schema.get("type") != "object":
            blocks.append(f"export type {type_name} = {_ts_type(schema, type_names)};")
            continue
        required = set(schema.get("required", []))
        lines = [f"export type {type_name} = {{"]
        for field, field_schema in schema.get("properties", {}).items():
            optional = "" if field in required else "?"
            lines.append(
                f"  {_ts_property_name(field)}{optional}: "
                f"{_ts_type(field_schema, type_names)};"
            )
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
    schemas = ir.get("schemas", {})
    type_names = _unique_identifiers(list(schemas), type_names=True)
    operation_names = _unique_identifiers(
        [str(operation["operationId"]) for operation in ir["operations"]]
    )
    blocks.extend(_render_interfaces(schemas))
    for operation in ir["operations"]:
        params: list[str] = []
        query_parameters = []
        path_parameters = []
        header_parameters = []
        parameter_names = _unique_identifiers(
            [str(parameter["name"]) for parameter in operation.get("parameters", [])]
        )
        used_identifiers = set(parameter_names.values())
        for parameter in operation.get("parameters", []):
            name = parameter["name"]
            identifier = parameter_names[name]
            if parameter.get("in") == "query":
                query_parameters.append((name, identifier))
            elif parameter.get("in") == "path":
                path_parameters.append((name, identifier))
            elif parameter.get("in") == "header":
                header_parameters.append((name, identifier, bool(parameter.get("required"))))
        body_schema = (
            (operation.get("requestBody") or {})
            .get("content", {})
            .get("application/json", {})
            .get("schema")
        )
        body_required = bool((operation.get("requestBody") or {}).get("required"))
        body_identifier = _fresh_identifier("body", used_identifiers) if body_schema else None
        required_parameters, optional_parameters = _parameter_groups(operation)
        for parameter in required_parameters:
            identifier = parameter_names[parameter["name"]]
            params.append(
                f"{identifier}: {_ts_type(parameter.get('schema', {}), type_names)}"
            )
        if body_schema and body_required:
            params.append(f"{body_identifier}: {_ts_type(body_schema, type_names)}")
        for parameter in optional_parameters:
            identifier = parameter_names[parameter["name"]]
            params.append(
                f"{identifier}?: {_ts_type(parameter.get('schema', {}), type_names)}"
            )
        if body_schema and not body_required:
            params.append(f"{body_identifier}?: {_ts_type(body_schema, type_names)}")
        args = ", ".join(params)
        return_type = (
            _ts_type(_success_schema(operation), type_names)
            if _success_schema(operation)
            else "unknown"
        )
        path_template = operation["path"]
        function_name = operation_names[str(operation["operationId"])]
        lines = [f"export async function {function_name}({args}): Promise<{return_type}> {{"]
        declared_path_names = {name for name, _ in path_parameters}
        placeholders = set(re.findall(r"\{([^{}]+)\}", path_template))
        if placeholders != declared_path_names:
            raise ValueError(
                f"Path placeholders do not match declared parameters for {operation['operationId']}"
            )
        encoded_parameters: dict[str, str] = {}
        for name, identifier in path_parameters:
            encoded_name = _fresh_identifier(
                f"encoded{identifier[:1].upper()}{identifier[1:]}", used_identifiers
            )
            encoded_parameters[name] = encoded_name
            lines.append(f"  const {encoded_name} = encodeURIComponent(String({identifier}));")
        path_identifier = _fresh_identifier("path", used_identifiers)
        url_identifier = _fresh_identifier("url", used_identifiers)
        headers_identifier = (
            _fresh_identifier("headers", used_identifiers)
            if header_parameters or body_schema
            else None
        )
        response_identifier = _fresh_identifier("response", used_identifiers)
        path_declaration = "let" if path_parameters else "const"
        lines.append(
            f"  {path_declaration} {path_identifier} = {_ts_string(path_template)};"
        )
        for name, identifier in path_parameters:
            lines.append(
                f"  {path_identifier} = {path_identifier}.replace("
                f"{_ts_string(f'{{{name}}}')}, {encoded_parameters[name]});"
            )
        lines.append(
            f"  const {url_identifier} = new URL({path_identifier}, window.location.origin);"
        )
        for name, identifier in query_parameters:
            lines.append(
                f"  if ({identifier} !== undefined) "
                f"{url_identifier}.searchParams.set({_ts_string(name)}, String({identifier}));"
            )
        if header_parameters or body_schema:
            lines.append(
                f"  const {headers_identifier}: Record<string, string> = {{}};"
            )
        for name, identifier, required in header_parameters:
            if required:
                lines.append(
                    f"  {headers_identifier}[{_ts_string(name)}] = String({identifier});"
                )
            else:
                lines.append(
                    f"  if ({identifier} !== undefined) "
                    f"{headers_identifier}[{_ts_string(name)}] = String({identifier});"
                )
        if body_schema:
            lines.append(
                f'  {headers_identifier}["Content-Type"] = "application/json";'
            )
        options = f"{{ method: '{operation['method']}'"
        if header_parameters or body_schema:
            options += f", headers: {headers_identifier}"
        if body_schema:
            options += f", body: JSON.stringify({body_identifier})"
        options += " }"
        lines.extend(
            [
                f"  const {response_identifier} = await fetch({url_identifier}, {options});",
                f"  if (!{response_identifier}.ok) throw new Error("
                f"`API request failed: ${{{response_identifier}.status}}`);",
                f"  return {response_identifier}.json() as Promise<{return_type}>;",
                "}",
            ]
        )
        blocks.append("\n".join(lines))
    return "\n\n".join(blocks) + "\n"


def generate_contract_tests(ir: dict[str, Any]) -> str:
    operation_names = _unique_identifiers(
        [str(operation["operationId"]) for operation in ir["operations"]]
    )
    lines = [
        "// Generated behavior tests for the typed API client.",
        "import { beforeEach, describe, expect, it, vi } from 'vitest';",
        "import * as client from './client';",
        "",
        "const fetchMock = vi.fn();",
        "vi.stubGlobal('fetch', fetchMock);",
        "vi.stubGlobal('window', { location: { origin: 'https://contract.test' } });",
        "",
        f"describe({_ts_string(str(ir['title']) + ' contract')}, () => {{",
        "  beforeEach(() => {",
        "    fetchMock.mockReset();",
        "    fetchMock.mockResolvedValue({ ok: true, json: async () => ({}) });",
        "  });",
    ]
    for operation in ir["operations"]:
        arguments: list[str] = []
        required_parameters, optional_parameters = _parameter_groups(operation)
        for parameter in required_parameters:
            arguments.append("'fixture'")
        body_schema = (
            (operation.get("requestBody") or {})
            .get("content", {})
            .get("application/json", {})
            .get("schema")
        )
        body_required = bool((operation.get("requestBody") or {}).get("required"))
        if body_schema and body_required:
            arguments.append("{} as never")
        for parameter in optional_parameters:
            arguments.append(
                "'fixture'"
                if parameter.get("in") in {"path", "header"}
                else "undefined"
            )
        if body_schema and not body_required:
            # Exercise the optional body path with a concrete JSON object.  Passing
            # undefined would make the generated client serialize an undefined body,
            # which is not parseable by the contract assertion below.
            arguments.append("{} as never")
        expected_path = operation["path"]
        for parameter in operation.get("parameters", []):
            if parameter.get("in") == "path":
                expected_path = expected_path.replace(f"{{{parameter['name']}}}", "fixture")
        description = (
            f"{operation['operationId']} sends {operation['method']} {operation['path']}"
        )
        function_name = operation_names[str(operation["operationId"])]
        test_lines = [
            f"  it({_ts_string(description)}, async () => {{",
            f"    await client.{function_name}({', '.join(arguments)});",
            "    const [requestUrl, options] = fetchMock.mock.calls[0];",
            f"    expect(new URL(requestUrl).pathname).toBe({_ts_string(expected_path)});",
            f"    expect(options.method).toBe({_ts_string(operation['method'])});",
        ]
        for parameter in operation.get("parameters", []):
            if parameter.get("in") == "header":
                test_lines.append(
                    f"    expect(options.headers[{_ts_string(parameter['name'])}]).toBe('fixture');"
                )
        if body_schema:
            test_lines.extend(
                [
                    '    expect(options.headers["Content-Type"]).toBe("application/json");',
                    "    expect(JSON.parse(options.body)).toEqual({});",
                ]
            )
        test_lines.append("  });")
        lines.extend(test_lines)
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
