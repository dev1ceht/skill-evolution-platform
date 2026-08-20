from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from pathlib import Path
from typing import Any

import yaml


UNITS = {
    "g": ("weight", 1.0, "g"),
    "kg": ("weight", 1000.0, "g"),
    "ml": ("volume", 1.0, "ml"),
    "l": ("volume", 1000.0, "ml"),
    "pcs": ("count", 1.0, "pcs"),
    "个": ("count", 1.0, "pcs"),
}


def load(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    value = json.loads(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    if not isinstance(value, dict):
        raise ValueError("receipt must be an object")
    return value


def dump(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix.lower() == ".json":
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    else:
        path.write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding="utf-8")


def convert(quantity: Any, unit: str) -> tuple[float, str, str]:
    try:
        amount = float(quantity)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"quantity must be numeric: {quantity}") from exc
    if amount <= 0:
        raise ValueError("received quantity must be positive")
    key = str(unit).strip().lower()
    if key not in UNITS:
        raise ValueError(f"unsupported unit: {unit}")
    dimension, factor, base_unit = UNITS[key]
    return round(amount * factor, 6), base_unit, dimension


def prepare(receipt: dict[str, Any]) -> dict[str, Any]:
    scope = receipt.get("scope")
    required = ("receipt_id", "idempotency_key", "purchase_order_id", "supplier_id", "received_at")
    if not isinstance(scope, dict) or not scope.get("school_id") or not scope.get("canteen_id"):
        raise ValueError("scope.school_id and scope.canteen_id are required")
    for field in required:
        if not receipt.get(field):
            raise ValueError(f"{field} is required")
    try:
        received_date = date.fromisoformat(str(receipt["received_at"])[:10])
    except ValueError as exc:
        raise ValueError("received_at must begin with an ISO date") from exc
    lines = receipt.get("lines")
    if not isinstance(lines, list) or not lines:
        raise ValueError("lines must be a non-empty list")
    batches = []
    for index, line in enumerate(lines, start=1):
        if not isinstance(line, dict) or not line.get("ingredient_id") or not line.get("batch_no"):
            raise ValueError(f"line {index} needs ingredient_id and batch_no")
        if not line.get("expires_on"):
            raise ValueError(f"line {index} needs expires_on")
        try:
            expires_on = date.fromisoformat(str(line["expires_on"])[:10])
        except ValueError as exc:
            raise ValueError(f"line {index} expires_on must be an ISO date") from exc
        if expires_on < received_date:
            raise ValueError(f"line {index} expires_on cannot precede received_at")
        base_amount, base_unit, dimension = convert(line.get("quantity"), str(line.get("unit", "")))
        batches.append(
            {
                "batch_id": f"{receipt['receipt_id']}-L{index}",
                "ingredient_id": str(line["ingredient_id"]),
                "batch_no": str(line["batch_no"]),
                "quantity": base_amount,
                "unit": base_unit,
                "dimension": dimension,
                "expires_on": str(line["expires_on"]),
                "traceability_code": f"TRACE-{receipt['receipt_id']}-L{index}",
            }
        )
    return {
        "schema_version": "1.0",
        "status": "ready_to_post",
        "adapter_status": "port-only",
        "scope": scope,
        "receipt_id": receipt["receipt_id"],
        "idempotency_key": receipt["idempotency_key"],
        "purchase_order_id": receipt["purchase_order_id"],
        "supplier_id": receipt["supplier_id"],
        "batches": batches,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare a canteen receipt for inventory posting")
    parser.add_argument("receipt", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = prepare(load(args.receipt))
        dump(args.output, result)
    except (OSError, ValueError, json.JSONDecodeError, yaml.YAMLError) as exc:
        print(f"receipt preparation stopped: {exc}", file=sys.stderr)
        return 1
    print(f"wrote {args.output} ({len(result['batches'])} batches)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
