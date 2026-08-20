from __future__ import annotations

import argparse
import copy
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import yaml


TRANSITIONS = {
    "draft": {"submitted"},
    "submitted": {"approved", "draft"},
    "approved": {"published", "draft"},
    "published": set(),
}


def load(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    value = json.loads(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    if not isinstance(value, dict):
        raise ValueError("menu document must be an object")
    return value


def dump(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix.lower() == ".json":
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    else:
        path.write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding="utf-8")


def transition(document: dict[str, Any], target: str, actor_role: str, reason: str | None) -> dict[str, Any]:
    scope = document.get("scope")
    menu = document.get("menu")
    if not isinstance(scope, dict) or not scope.get("school_id") or not scope.get("canteen_id"):
        raise ValueError("scope.school_id and scope.canteen_id are required")
    if not isinstance(menu, dict):
        raise ValueError("menu is required")
    if not menu.get("id") or not menu.get("date") or not menu.get("meal"):
        raise ValueError("menu.id, menu.date and menu.meal are required")
    dishes = menu.get("dishes")
    if not isinstance(dishes, list) or not dishes:
        raise ValueError("menu.dishes must be a non-empty list")
    for dish in dishes:
        if not isinstance(dish, dict) or not dish.get("recipe_id") or not dish.get("name"):
            raise ValueError("each dish needs name and recipe_id")
        try:
            if float(dish.get("servings", 0)) <= 0:
                raise ValueError("dish servings must be positive")
        except (TypeError, ValueError) as exc:
            raise ValueError("dish servings must be positive") from exc
    current = str(menu.get("status", "draft"))
    if target not in TRANSITIONS.get(current, set()):
        raise ValueError(f"invalid menu transition: {current} -> {target}")
    if target == "approved" and actor_role not in {"MENU_MANAGER", "ADMIN"}:
        raise ValueError("only MENU_MANAGER or ADMIN can approve a menu")
    if target == "published" and actor_role not in {"MENU_MANAGER", "ADMIN"}:
        raise ValueError("only MENU_MANAGER or ADMIN can publish a menu")
    if target == "draft" and not reason:
        raise ValueError("a rejection reason is required when returning to draft")

    result = copy.deepcopy(document)
    result_menu = result["menu"]
    history = list(result_menu.get("history", []))
    history.append(
        {
            "from": current,
            "to": target,
            "actor_role": actor_role,
            "reason": reason,
            "at": datetime.now(timezone.utc).isoformat(),
        }
    )
    result_menu["status"] = target
    result_menu["history"] = history
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="Apply a canteen menu business state transition")
    parser.add_argument("menu", type=Path)
    parser.add_argument("--to", required=True, choices=sorted({item for values in TRANSITIONS.values() for item in values}))
    parser.add_argument("--actor-role", required=True)
    parser.add_argument("--reason")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = transition(load(args.menu), args.to, args.actor_role, args.reason)
        dump(args.output, result)
    except (OSError, ValueError, json.JSONDecodeError, yaml.YAMLError) as exc:
        print(f"menu transition stopped: {exc}", file=sys.stderr)
        return 1
    print(f"wrote {args.output} ({result['menu']['status']})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
