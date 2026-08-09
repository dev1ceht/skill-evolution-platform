#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import yaml


METHODS = ("get", "post", "put", "patch", "delete", "head", "options")


def main() -> None:
    parser = argparse.ArgumentParser(description="Normalize an OpenAPI 3 document into API IR")
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", "-o", type=Path, required=True)
    args = parser.parse_args()
    document = yaml.safe_load(args.input.read_text(encoding="utf-8"))
    if not isinstance(document, dict) or not str(document.get("openapi", "")).startswith("3."):
        raise SystemExit("Expected an OpenAPI 3 JSON or YAML document")
    canonical = json.dumps(document, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    digest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    operations = []
    for path in sorted(document.get("paths", {})):
        item = document["paths"][path]
        for method in METHODS:
            operation = item.get(method)
            if not isinstance(operation, dict):
                continue
            escaped = path.replace("~", "~0").replace("/", "~1")
            operations.append(
                {
                    "operationId": operation.get("operationId", f"{method}_{escaped}"),
                    "method": method.upper(),
                    "path": path,
                    "tags": operation.get("tags", ["default"]),
                    "parameters": [*item.get("parameters", []), *operation.get("parameters", [])],
                    "requestBody": operation.get("requestBody"),
                    "responses": operation.get("responses", {}),
                    "provenance": {"documentHash": digest, "pointer": f"/paths/{escaped}/{method}"},
                }
            )
    result = {
        "title": document.get("info", {}).get("title", "Untitled API"),
        "version": document.get("info", {}).get("version", "0.0.0"),
        "documentHash": digest,
        "schemas": document.get("components", {}).get("schemas", {}),
        "operations": operations,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"normalized {len(operations)} operations -> {args.output}")


if __name__ == "__main__":
    main()

