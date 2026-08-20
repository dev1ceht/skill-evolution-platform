from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import yaml


def load(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    value = json.loads(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    if not isinstance(value, dict):
        raise ValueError("ledger cycle must be an object")
    return value


def dump(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix.lower() == ".json":
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    else:
        path.write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding="utf-8")


def check_cycle(document: dict[str, Any]) -> dict[str, Any]:
    scope = document.get("scope")
    if not isinstance(scope, dict) or not scope.get("school_id") or not scope.get("canteen_id"):
        raise ValueError("scope.school_id and scope.canteen_id are required")
    if not document.get("cycle_id"):
        raise ValueError("cycle_id is required")
    required = document.get("required")
    records = document.get("records", [])
    if not isinstance(required, list) or not required:
        raise ValueError("required must be a non-empty list")
    if not isinstance(records, list):
        raise ValueError("records must be a list")
    required_codes = {str(code) for code in required}
    submitted: set[str] = set()
    for record in records:
        if isinstance(record, dict) and record.get("code"):
            code = str(record["code"])
            if code in required_codes:
                submitted.add(code)
    missing = sorted(required_codes - submitted)
    complete = not missing
    return {
        "schema_version": "1.0",
        "scope": scope,
        "cycle_id": document["cycle_id"],
        "status": "COMPLETE" if complete else "INCOMPLETE",
        "alert_action": "CLEARED" if complete else "KEEP_OPEN",
        "required_count": len(required_codes),
        "submitted_count": len(submitted),
        "missing": missing,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Check a canteen ledger cycle")
    parser.add_argument("cycle", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = check_cycle(load(args.cycle))
        dump(args.output, result)
    except (OSError, ValueError, json.JSONDecodeError, yaml.YAMLError) as exc:
        print(f"ledger check stopped: {exc}", file=sys.stderr)
        return 1
    print(f"wrote {args.output} ({result['status']})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
