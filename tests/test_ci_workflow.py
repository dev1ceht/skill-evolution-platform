from pathlib import Path

import yaml


def test_ci_workflow_covers_business_product_and_evidence_boundaries() -> None:
    workflow_path = Path(".github/workflows/ci.yml")
    workflow = yaml.safe_load(workflow_path.read_text(encoding="utf-8"))

    assert workflow["permissions"] == {"contents": "read"}
    assert set(workflow["jobs"]) == {"quality", "frontend", "backend", "runtime"}
    assert all(job.get("timeout-minutes", 0) > 0 for job in workflow["jobs"].values())
    assert workflow["jobs"]["runtime"]["needs"] == ["quality", "frontend", "backend"]

    source = workflow_path.read_text(encoding="utf-8")
    for command in (
        "python -m pytest",
        "python skills/smart-canteen-sop/scripts/validate_sop_manifest.py",
        "python skills/frontend-api-integration/scripts/normalize_openapi.py",
        "npm ci",
        "npm test",
        "npm run build",
        "mvn --batch-mode test",
        "verify-stack.ps1",
        "verify-mysql-workflow.ps1",
    ):
        assert command in source
    assert "actions/checkout@v7" in source
    assert "actions/setup-python@v7" in source
    assert "actions/setup-node@v7" in source
    assert "actions/setup-java@v5" in source
    assert "actions/upload-artifact@v7" in source
    assert "outputs/verification/*.json" in source
