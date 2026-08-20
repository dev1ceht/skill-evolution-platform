from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

import yaml


UNIT_FACTORS = {
    "g": ("weight", 1.0, "g"),
    "kg": ("weight", 1000.0, "g"),
    "ml": ("volume", 1.0, "ml"),
    "l": ("volume", 1000.0, "ml"),
    "pcs": ("count", 1.0, "pcs"),
    "个": ("count", 1.0, "pcs"),
    "份": ("count", 1.0, "pcs"),
}


def load_document(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    value = json.loads(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain an object")
    return value


def dump_document(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix.lower() == ".json":
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    else:
        path.write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding="utf-8")


def unit_info(unit: str) -> tuple[str, float, str]:
    key = str(unit).strip().lower()
    if key not in UNIT_FACTORS:
        raise ValueError(f"unsupported unit: {unit}")
    return UNIT_FACTORS[key]


def base_quantity(quantity: Any, unit: str) -> tuple[float, str, str]:
    try:
        amount = float(quantity)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"quantity must be numeric: {quantity}") from exc
    if amount <= 0:
        raise ValueError(f"quantity must be positive: {quantity}")
    dimension, factor, base_unit = unit_info(unit)
    return amount * factor, base_unit, dimension


def load_mapping(menu: dict[str, Any], path: Path | None) -> dict[str, str]:
    mapping: dict[str, str] = {}
    embedded = menu.get("product_mapping", {})
    if isinstance(embedded, dict):
        mapping.update({str(k): str(v) for k, v in embedded.items()})
    if path:
        external = load_document(path)
        external_mapping = external.get("product_mapping", external.get("mapping", external))
        if not isinstance(external_mapping, dict):
            raise ValueError("mapping file must contain an object")
        mapping.update({str(k): str(v) for k, v in external_mapping.items()})
    return mapping


def build_order(menu: dict[str, Any], inventory: dict[str, Any], mapping: dict[str, str]) -> dict[str, Any]:
    scope = menu.get("scope")
    if not isinstance(scope, dict) or not scope.get("school_id") or not scope.get("canteen_id"):
        raise ValueError("scope.school_id and scope.canteen_id are required")
    if menu.get("status") != "published":
        raise ValueError("only a published menu can generate a formal order")
    meals = menu.get("meals")
    if not isinstance(meals, list) or not meals:
        raise ValueError("meals must be a non-empty list")

    needs: dict[str, float] = defaultdict(float)
    units: dict[str, str] = {}
    dimensions: dict[str, str] = {}
    for meal in meals:
        if not isinstance(meal, dict):
            raise ValueError("each meal must be an object")
        try:
            servings = float(meal["servings"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError("each meal needs numeric servings") from exc
        if servings <= 0:
            raise ValueError("servings must be positive")
        items = meal.get("items", meal.get("recipe"))
        if not isinstance(items, list):
            raise ValueError("each meal needs items or recipe")
        for item in items:
            if not isinstance(item, dict) or not item.get("ingredient"):
                raise ValueError("each recipe item needs ingredient")
            ingredient = str(item["ingredient"])
            quantity, base_unit, dimension = base_quantity(
                item.get("quantity_per_serving"), str(item.get("unit", ""))
            )
            if ingredient in dimensions and dimensions[ingredient] != dimension:
                raise ValueError(f"unit dimension conflict for {ingredient}")
            dimensions[ingredient] = dimension
            units[ingredient] = base_unit
            needs[ingredient] += quantity * servings

    available: dict[str, float] = defaultdict(float)
    safety: dict[str, float] = defaultdict(float)
    inventory_items = inventory.get("inventory", inventory.get("items", []))
    if inventory_items is None:
        inventory_items = []
    if not isinstance(inventory_items, list):
        raise ValueError("inventory must be a list")
    for item in inventory_items:
        if not isinstance(item, dict) or not item.get("ingredient"):
            raise ValueError("each inventory item needs ingredient")
        ingredient = str(item["ingredient"])
        quantity, base_unit, dimension = base_quantity(item.get("quantity"), str(item.get("unit", "")))
        if ingredient in dimensions and dimensions[ingredient] != dimension:
            raise ValueError(f"inventory unit dimension conflicts for {ingredient}")
        if ingredient in units and units[ingredient] != base_unit:
            raise ValueError(f"inventory base unit conflicts for {ingredient}")
        available[ingredient] += quantity
        safety_amount = item.get("safety_stock", 0)
        if safety_amount:
            safety_quantity, _, safety_dimension = base_quantity(safety_amount, str(item.get("unit", "")))
            if safety_dimension != dimension:
                raise ValueError(f"safety stock unit conflicts for {ingredient}")
            safety[ingredient] += safety_quantity

    rows: list[dict[str, Any]] = []
    unmatched: list[dict[str, Any]] = []
    for ingredient in sorted(needs):
        quantity = max(0.0, needs[ingredient] + safety[ingredient] - available[ingredient])
        if quantity == 0:
            continue
        row = {
            "ingredient": ingredient,
            "product": mapping.get(ingredient),
            "quantity": round(quantity, 6),
            "unit": units[ingredient],
            "required": round(needs[ingredient], 6),
            "available": round(available[ingredient], 6),
            "safety_stock": round(safety[ingredient], 6),
        }
        if not row["product"]:
            unmatched.append(row)
        else:
            rows.append(row)

    return {
        "schema_version": "1.0",
        "scope": scope,
        "period": menu.get("period"),
        "status": "needs_mapping" if unmatched else "ready_for_confirmation",
        "rows": rows,
        "unmatched": unmatched,
        "source": {"meal_count": len(meals), "inventory_items": len(inventory_items)},
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate a canteen order sheet from menu and inventory snapshots")
    parser.add_argument("menu", type=Path)
    parser.add_argument("--inventory", type=Path)
    parser.add_argument("--mapping", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--allow-unmatched", action="store_true")
    args = parser.parse_args()
    try:
        menu = load_document(args.menu)
        inventory = load_document(args.inventory) if args.inventory else {}
        result = build_order(menu, inventory, load_mapping(menu, args.mapping))
        dump_document(args.output, result)
    except (OSError, ValueError, json.JSONDecodeError, yaml.YAMLError) as exc:
        print(f"order generation failed: {exc}", file=sys.stderr)
        return 1
    if result["unmatched"] and not args.allow_unmatched:
        print("unmatched ingredients require confirmation", file=sys.stderr)
        return 2
    print(f"wrote {args.output} ({result['status']})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
