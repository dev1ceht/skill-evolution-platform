from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import yaml


ORDER = ["menu", "purchase_order", "receipt", "inventory_batch", "stock_out"]


def load(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    value = json.loads(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    if not isinstance(value, dict):
        raise ValueError("trace facts must be an object")
    return value


def dump(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix.lower() == ".json":
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    else:
        path.write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding="utf-8")


def build(document: dict[str, Any]) -> dict[str, Any]:
    scope = document.get("scope")
    trace_code = document.get("trace_code")
    facts = document.get("facts")
    if not isinstance(scope, dict) or not scope.get("school_id") or not scope.get("canteen_id"):
        raise ValueError("scope.school_id and scope.canteen_id are required")
    if not trace_code:
        raise ValueError("trace_code is required")
    if not isinstance(facts, list):
        raise ValueError("facts must be a list")
    by_type: dict[str, dict[str, Any]] = {}
    for fact in facts:
        if not isinstance(fact, dict) or not fact.get("type") or not fact.get("id"):
            raise ValueError("each fact needs type and id")
        if fact.get("trace_code") != trace_code:
            raise ValueError(f"fact {fact['id']} does not belong to trace code")
        by_type.setdefault(str(fact["type"]), fact)
    missing = [fact_type for fact_type in ORDER if fact_type not in by_type]
    chain = [by_type[fact_type] for fact_type in ORDER if fact_type in by_type]
    return {
        "schema_version": "1.0",
        "status": "complete" if not missing else "incomplete",
        "scope": scope,
        "trace_code": trace_code,
        "chain": chain,
        "missing_facts": missing,
        "required_facts": ORDER,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Build a canteen traceability chain")
    parser.add_argument("facts", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = build(load(args.facts))
        dump(args.output, result)
    except (OSError, ValueError, json.JSONDecodeError, yaml.YAMLError) as exc:
        print(f"traceability build stopped: {exc}", file=sys.stderr)
        return 1
    print(f"wrote {args.output} ({result['status']})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
