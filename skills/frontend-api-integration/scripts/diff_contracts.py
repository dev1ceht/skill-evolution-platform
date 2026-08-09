#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare two normalized API IR files")
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--output", "-o", type=Path, required=True)
    args = parser.parse_args()
    before = json.loads(args.before.read_text(encoding="utf-8"))
    after = json.loads(args.after.read_text(encoding="utf-8"))
    old = {(item["method"], item["path"]) for item in before["operations"]}
    new = {(item["method"], item["path"]) for item in after["operations"]}
    removed = sorted(old - new)
    added = sorted(new - old)
    result = {
        "beforeHash": before["documentHash"],
        "afterHash": after["documentHash"],
        "added": [{"method": method, "path": path} for method, path in added],
        "removed": [{"method": method, "path": path} for method, path in removed],
        "breaking": [{"type": "removed-operation", "method": method, "path": path} for method, path in removed],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"found {len(result['breaking'])} breaking changes -> {args.output}")


if __name__ == "__main__":
    main()
