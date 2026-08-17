#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "src"))

from smart_canteen_contracts.contracts import diff_contracts


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare two normalized API IR files")
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--output", "-o", type=Path, required=True)
    args = parser.parse_args()
    before = json.loads(args.before.read_text(encoding="utf-8"))
    after = json.loads(args.after.read_text(encoding="utf-8"))
    result = diff_contracts(before, after)
    result["beforeHash"] = before["documentHash"]
    result["afterHash"] = after["documentHash"]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"found {len(result['breaking'])} breaking changes -> {args.output}")


if __name__ == "__main__":
    main()
