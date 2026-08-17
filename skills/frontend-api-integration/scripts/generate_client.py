#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "src"))

from smart_canteen_contracts.contracts import generate_typescript_client


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a typed fetch client from API IR")
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", "-o", type=Path, required=True)
    args = parser.parse_args()
    ir = json.loads(args.input.read_text(encoding="utf-8"))
    output = generate_typescript_client(ir)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(output, encoding="utf-8")
    print(f"generated {len(ir['operations'])} operations -> {args.output}")


if __name__ == "__main__":
    main()
