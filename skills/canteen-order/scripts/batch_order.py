from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

import yaml


def load(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    value = json.loads(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    if not isinstance(value, dict):
        raise ValueError("order sheet must be an object")
    return value


def write(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix.lower() == ".json":
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    else:
        path.write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare a confirmed canteen order submission")
    parser.add_argument("order_sheet", type=Path)
    parser.add_argument("--existing", type=Path, help="existing submission record used for idempotency comparison")
    parser.add_argument("--confirm", action="store_true", help="confirm the order sheet for submission")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        order = load(args.order_sheet)
        if order.get("status") != "ready_for_confirmation":
            raise ValueError("only a ready_for_confirmation order can be submitted")
        if not order.get("rows"):
            raise ValueError("order has no purchasable rows")
        if order.get("unmatched"):
            raise ValueError("unmatched ingredients must be resolved before submission")
        if not args.confirm:
            raise ValueError("explicit --confirm is required before batch submission")
        payload = {"scope": order.get("scope"), "period": order.get("period"), "rows": order.get("rows", [])}
        canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        idem = "order-" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:20]
        result = {
            "schema_version": "1.0",
            "status": "prepared",
            "adapter_status": "port-only",
            "idempotency_key": idem,
            "confirmed": True,
            "order": payload,
            "message": "No external supplier adapter is configured; this is a submission record only.",
        }
        if args.existing:
            existing = load(args.existing)
            if existing.get("idempotency_key") == idem:
                if existing.get("order") == payload:
                    result["status"] = "idempotent_replay"
                else:
                    result["status"] = "conflict"
                    result["conflict_reason"] = "same idempotency key with different order payload"
        write(args.output, result)
    except (OSError, ValueError, json.JSONDecodeError, yaml.YAMLError) as exc:
        print(f"batch submission stopped: {exc}", file=sys.stderr)
        return 1
    print(f"wrote {args.output} ({result['status']})")
    return 2 if result["status"] == "conflict" else 0


if __name__ == "__main__":
    raise SystemExit(main())
