from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

import yaml


SEVERITIES = {"LOW", "MEDIUM", "HIGH"}


def load(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    value = json.loads(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    if not isinstance(value, dict):
        raise ValueError("safety event must be an object")
    return value


def dump(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix.lower() == ".json":
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    else:
        path.write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding="utf-8")


def accept(event: dict[str, Any], existing: dict[str, Any] | None = None) -> dict[str, Any]:
    scope = event.get("scope")
    for field in ("source", "event_id", "occurred_at", "description"):
        if not event.get(field):
            raise ValueError(f"{field} is required")
    if not isinstance(scope, dict) or not scope.get("school_id") or not scope.get("canteen_id"):
        raise ValueError("scope.school_id and scope.canteen_id are required")
    severity = str(event.get("severity", "MEDIUM")).upper()
    if severity not in SEVERITIES:
        raise ValueError("severity must be LOW, MEDIUM, or HIGH")
    dedupe_key = f"{event['source']}:{event['event_id']}"
    fingerprint_payload = json.dumps(
        {
            "scope": scope,
            "source": event["source"],
            "event_id": event["event_id"],
            "occurred_at": event["occurred_at"],
            "severity": severity,
            "description": event["description"],
            "evidence": list(event.get("evidence", [])),
        },
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    fingerprint = hashlib.sha256(fingerprint_payload.encode("utf-8")).hexdigest()[:20]
    result = {
        "schema_version": "1.0",
        "status": "accepted",
        "disposal_status": "no_action" if severity == "LOW" else "needs_disposal",
        "adapter_status": "port-only",
        "scope": scope,
        "source": event["source"],
        "event_id": event["event_id"],
        "dedupe_key": dedupe_key,
        "fingerprint": fingerprint,
        "severity": severity,
        "occurred_at": event["occurred_at"],
        "description": event["description"],
        "evidence": list(event.get("evidence", [])),
    }
    if existing and existing.get("dedupe_key") == dedupe_key:
        if existing.get("fingerprint") == fingerprint:
            result["status"] = "idempotent_replay"
            result["existing_fingerprint"] = fingerprint
        else:
            result["status"] = "conflict"
            result["conflict_reason"] = "same source and event_id with different payload"
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="Accept and normalize a canteen food-safety event")
    parser.add_argument("event", type=Path)
    parser.add_argument("--existing", type=Path, help="existing normalized event used for idempotency comparison")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = accept(load(args.event), load(args.existing) if args.existing else None)
        dump(args.output, result)
    except (OSError, ValueError, json.JSONDecodeError, yaml.YAMLError) as exc:
        print(f"safety event stopped: {exc}", file=sys.stderr)
        return 1
    print(f"wrote {args.output} ({result['status']})")
    return 2 if result["status"] == "conflict" else 0


if __name__ == "__main__":
    raise SystemExit(main())
