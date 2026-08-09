#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def ts_type(schema: dict[str, Any]) -> str:
    if "$ref" in schema:
        return str(schema["$ref"]).rsplit("/", 1)[-1]
    if schema.get("type") == "array":
        return f"Array<{ts_type(schema.get('items', {}))}>"
    return {"integer": "number", "number": "number", "boolean": "boolean", "object": "Record<string, unknown>"}.get(schema.get("type"), "string")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a typed fetch client from API IR")
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", "-o", type=Path, required=True)
    args = parser.parse_args()
    ir = json.loads(args.input.read_text(encoding="utf-8"))
    blocks = [f"// Generated from {ir['title']} {ir['version']}.\n// Source hash: {ir['documentHash']}"]
    for name, schema in ir.get("schemas", {}).items():
        if schema.get("type") != "object":
            continue
        required = set(schema.get("required", []))
        fields = [f"  {field}{'' if field in required else '?'}: {ts_type(value)};" for field, value in schema.get("properties", {}).items()]
        blocks.append(f"export type {name} = {{\n" + "\n".join(fields) + "\n};")
    for operation in ir["operations"]:
        parameters = []
        for item in operation.get("parameters", []):
            parameters.append(f"{item['name']}{'' if item.get('required') else '?'}: {ts_type(item.get('schema', {}))}")
        blocks.append(
            f"export async function {operation['operationId']}({', '.join(parameters)}): Promise<unknown> {{\n"
            f"  const response = await fetch('{operation['path']}', {{ method: '{operation['method']}' }});\n"
            "  if (!response.ok) throw new Error(`API request failed: ${response.status}`);\n"
            "  return response.json();\n}"
        )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n\n".join(blocks) + "\n", encoding="utf-8")
    print(f"generated {len(ir['operations'])} operations -> {args.output}")


if __name__ == "__main__":
    main()

