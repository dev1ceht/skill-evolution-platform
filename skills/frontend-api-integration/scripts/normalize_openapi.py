#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "src"))

from smart_canteen_contracts.contracts import normalize_openapi


def main() -> None:
    parser = argparse.ArgumentParser(description="Normalize an OpenAPI 3 document into API IR")
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", "-o", type=Path, required=True)
    args = parser.parse_args()
    result = normalize_openapi(args.input.read_text(encoding="utf-8"))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"normalized {len(result['operations'])} operations -> {args.output}")


if __name__ == "__main__":
    main()
