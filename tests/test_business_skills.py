from __future__ import annotations

import subprocess
import sys
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]


def write_yaml(path: Path, value: dict) -> None:
    path.write_text(yaml.safe_dump(value, allow_unicode=True, sort_keys=False), encoding="utf-8")


def run_script(script: str, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(ROOT / script), *args],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )


def test_canteen_order_generates_and_confirms_batch_order(tmp_path: Path) -> None:
    menu = tmp_path / "menu.yaml"
    inventory = tmp_path / "inventory.yaml"
    mapping = tmp_path / "mapping.yaml"
    order = tmp_path / "order.yaml"
    submission = tmp_path / "submission.yaml"
    write_yaml(
        menu,
        {
            "scope": {"school_id": "SCHOOL-001", "canteen_id": "CANTEEN-001"},
            "period": "2026-W34",
            "status": "published",
            "meals": [
                {
                    "date": "2026-08-20",
                    "meal": "lunch",
                    "servings": 100,
                    "items": [{"ingredient": "rice", "quantity_per_serving": 0.12, "unit": "kg"}],
                }
            ],
        },
    )
    write_yaml(inventory, {"inventory": [{"ingredient": "rice", "quantity": 5, "unit": "kg", "safety_stock": 1}]})
    write_yaml(mapping, {"mapping": {"rice": "大米"}})

    generated = run_script(
        "skills/canteen-order/scripts/generate_order.py",
        str(menu),
        "--inventory",
        str(inventory),
        "--mapping",
        str(mapping),
        "--output",
        str(order),
    )
    assert generated.returncode == 0, generated.stdout + generated.stderr
    order_doc = yaml.safe_load(order.read_text(encoding="utf-8"))
    assert order_doc["status"] == "ready_for_confirmation"
    assert order_doc["rows"][0]["quantity"] == 8000.0

    submitted = run_script(
        "skills/canteen-order/scripts/batch_order.py",
        str(order),
        "--confirm",
        "--output",
        str(submission),
    )
    assert submitted.returncode == 0, submitted.stdout + submitted.stderr
    submission_doc = yaml.safe_load(submission.read_text(encoding="utf-8"))
    assert submission_doc["adapter_status"] == "port-only"
    assert submission_doc["idempotency_key"].startswith("order-")
    replay = tmp_path / "submission-replay.yaml"
    replayed = run_script(
        "skills/canteen-order/scripts/batch_order.py",
        str(order),
        "--existing",
        str(submission),
        "--confirm",
        "--output",
        str(replay),
    )
    assert replayed.returncode == 0, replayed.stdout + replayed.stderr
    assert yaml.safe_load(replay.read_text(encoding="utf-8"))["status"] == "idempotent_replay"


def test_business_workflows_produce_domain_results(tmp_path: Path) -> None:
    menu = tmp_path / "menu.yaml"
    write_yaml(
        menu,
        {
            "scope": {"school_id": "SCHOOL-001", "canteen_id": "CANTEEN-001"},
            "menu": {
                "id": "MENU-001",
                "date": "2026-08-20",
                "meal": "lunch",
                "version": 1,
                "status": "draft",
                "dishes": [{"name": "番茄炒蛋", "recipe_id": "RECIPE-001", "servings": 100}],
            },
        },
    )
    submitted = tmp_path / "submitted.yaml"
    result = run_script(
        "skills/canteen-menu/scripts/transition_menu.py",
        str(menu),
        "--to",
        "submitted",
        "--actor-role",
        "CANTEEN_STAFF",
        "--output",
        str(submitted),
    )
    assert result.returncode == 0, result.stdout + result.stderr
    assert yaml.safe_load(submitted.read_text(encoding="utf-8"))["menu"]["status"] == "submitted"

    receipt = tmp_path / "receipt.yaml"
    receipt_output = tmp_path / "receipt-output.yaml"
    write_yaml(
        receipt,
        {
            "scope": {"school_id": "SCHOOL-001", "canteen_id": "CANTEEN-001"},
            "receipt_id": "RECEIPT-001",
            "idempotency_key": "receive-001",
            "purchase_order_id": "PO-001",
            "supplier_id": "SUPPLIER-001",
            "received_at": "2026-08-20T09:30:00+08:00",
            "lines": [{"ingredient_id": "rice", "quantity": 50, "unit": "kg", "batch_no": "B-001", "expires_on": "2026-09-20"}],
        },
    )
    result = run_script(
        "skills/canteen-inventory/scripts/receive_goods.py",
        str(receipt),
        "--output",
        str(receipt_output),
    )
    assert result.returncode == 0, result.stdout + result.stderr
    assert yaml.safe_load(receipt_output.read_text(encoding="utf-8"))["batches"][0]["quantity"] == 50000.0

    cycle = tmp_path / "cycle.yaml"
    cycle_output = tmp_path / "cycle-output.yaml"
    write_yaml(
        cycle,
        {
            "scope": {"school_id": "SCHOOL-001", "canteen_id": "CANTEEN-001"},
            "cycle_id": "2026-08-20",
            "required": ["MORNING_CHECK", "SAMPLE"],
            "records": [{"code": "MORNING_CHECK"}, {"code": "SAMPLE"}],
        },
    )
    result = run_script("skills/canteen-ledger/scripts/check_cycle.py", str(cycle), "--output", str(cycle_output))
    assert result.returncode == 0, result.stdout + result.stderr
    assert yaml.safe_load(cycle_output.read_text(encoding="utf-8"))["alert_action"] == "CLEARED"

    event = tmp_path / "event.yaml"
    event_output = tmp_path / "event-output.yaml"
    write_yaml(
        event,
        {
            "scope": {"school_id": "SCHOOL-001", "canteen_id": "CANTEEN-001"},
            "source": "MORNING_INSPECTION",
            "event_id": "EVENT-001",
            "occurred_at": "2026-08-20T07:30:00+08:00",
            "severity": "HIGH",
            "description": "留样柜温度异常",
        },
    )
    result = run_script("skills/canteen-safety/scripts/accept_event.py", str(event), "--output", str(event_output))
    assert result.returncode == 0, result.stdout + result.stderr
    assert yaml.safe_load(event_output.read_text(encoding="utf-8"))["disposal_status"] == "needs_disposal"
    replay_output = tmp_path / "event-replay.yaml"
    result = run_script(
        "skills/canteen-safety/scripts/accept_event.py",
        str(event),
        "--existing",
        str(event_output),
        "--output",
        str(replay_output),
    )
    assert result.returncode == 0, result.stdout + result.stderr
    assert yaml.safe_load(replay_output.read_text(encoding="utf-8"))["status"] == "idempotent_replay"

    facts = tmp_path / "facts.yaml"
    trace_output = tmp_path / "trace-output.yaml"
    write_yaml(
        facts,
        {
            "scope": {"school_id": "SCHOOL-001", "canteen_id": "CANTEEN-001"},
            "trace_code": "TRACE-001",
            "facts": [{"type": fact_type, "id": f"{fact_type}-001", "trace_code": "TRACE-001"} for fact_type in ["menu", "purchase_order", "receipt", "inventory_batch", "stock_out"]],
        },
    )
    result = run_script("skills/canteen-traceability/scripts/build_trace.py", str(facts), "--output", str(trace_output))
    assert result.returncode == 0, result.stdout + result.stderr
    trace = yaml.safe_load(trace_output.read_text(encoding="utf-8"))
    assert trace["status"] == "complete"
    assert [fact["type"] for fact in trace["chain"]] == ["menu", "purchase_order", "receipt", "inventory_batch", "stock_out"]
