from pathlib import Path
import subprocess
import sys

import yaml


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "docs" / "smart-canteen" / "sop-manifests.yaml"
RUN = ROOT / "sop-runs" / "menu-to-traceability.yaml"
VALIDATOR = ROOT / "skills" / "smart-canteen-sop" / "scripts" / "validate_sop_manifest.py"


def test_sop_manifest_and_run_are_valid() -> None:
    result = subprocess.run(
        [sys.executable, str(VALIDATOR), "--run", str(RUN)],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    assert "Validated 8 SOPs and 1 compositions" in result.stdout


def test_closed_loop_run_covers_menu_procurement_inventory_and_traceability() -> None:
    manifest = yaml.safe_load(MANIFEST.read_text(encoding="utf-8"))
    run = yaml.safe_load(RUN.read_text(encoding="utf-8"))
    composition = next(
        item for item in manifest["compositions"] if item["id"] == run["composition_id"]
    )
    observed = [step["sop_id"] for step in run["steps"]]
    assert observed == composition["sops"]
    assert run["scope"] == {"school_id": "SCHOOL-001", "canteen_id": "CANTEEN-001"}
    assert run["status"] == "passed"
