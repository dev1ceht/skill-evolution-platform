from __future__ import annotations

import csv
import json
import os
import subprocess
import sys
from pathlib import Path


def _write_csv(path: Path, rows: list[dict[str, object]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def _benchmark_row(**overrides: object) -> dict[str, object]:
    row: dict[str, object] = {
        "task_id": "TASK-001",
        "task_name": "Menu approval page",
        "project": "smart-canteen",
        "framework": "vue",
        "operation_count": 4,
        "traditional_minutes": 100,
        "skill_minutes": 10,
        "traditional_rework_count": 2,
        "skill_rework_count": 0,
        "traditional_defect_count": 1,
        "skill_defect_count": 0,
        "traditional_first_pass": "false",
        "skill_first_pass": "true",
        "skill_token_count": 4000,
        "sample_type": "synthetic",
        "recorded_at": "2026-08-01T08:00:00+08:00",
        "source_ref": "demo:TASK-001",
    }
    row.update(overrides)
    return row


def test_benchmark_cli_builds_auditable_paired_report(tmp_path: Path) -> None:
    source = tmp_path / "benchmark.csv"
    _write_csv(
        source,
        [
            _benchmark_row(),
            _benchmark_row(
                task_id="TASK-002",
                task_name="Inventory receipt page",
                framework="react",
                operation_count=2,
                traditional_minutes=60,
                skill_minutes=5,
                traditional_rework_count=0,
                skill_rework_count=1,
                traditional_defect_count=0,
                skill_defect_count=0,
                traditional_first_pass="true",
                skill_first_pass="false",
                skill_token_count=2000,
                recorded_at="2026-08-02T08:00:00+08:00",
                source_ref="demo:TASK-002",
            ),
        ],
    )

    completed = subprocess.run(
        [
            sys.executable,
            "-m",
            "skill_evolution.cli",
            "benchmark",
            "--input",
            str(source),
            "--project-root",
            str(tmp_path),
            "--name",
            "acceptance",
        ],
        check=False,
        capture_output=True,
        text=True,
        env={**os.environ, "PYTHONPATH": str(Path.cwd() / "src")},
    )

    assert completed.returncode == 0, completed.stderr
    result = json.loads(completed.stdout)
    report = json.loads((tmp_path / "outputs/benchmarks/acceptance.json").read_text("utf-8"))
    dashboard = (tmp_path / "outputs/benchmarks/acceptance.html").read_text("utf-8")

    assert result["report"] == str(tmp_path / "outputs/benchmarks/acceptance.json")
    assert report["dataset"] == {
        "taskCount": 2,
        "realTaskCount": 0,
        "syntheticTaskCount": 2,
        "operationCount": 6,
        "metricScope": "synthetic-demo",
        "metricTaskCount": 2,
    }
    assert report["durationMinutes"] == {
        "traditionalP50": 80.0,
        "traditionalP90": 96.0,
        "skillP50": 7.5,
        "skillP90": 9.5,
        "traditionalTotal": 160.0,
        "skillTotal": 15.0,
        "totalSpeedup": 10.6667,
        "pairedSpeedupP50": 11.0,
        "pairedSpeedupP90": 11.8,
    }
    assert report["quality"]["traditionalFirstPassRate"] == 0.5
    assert report["quality"]["skillFirstPassRate"] == 0.5
    assert report["tokens"] == {"skillTotal": 6000, "skillP50": 3000.0, "skillP90": 3800.0}
    assert report["claim"] == {
        "minimumRealTasks": 20,
        "targetSpeedup": 20.0,
        "status": "insufficient-real-samples",
        "supported": False,
    }
    assert len(report["provenance"]["inputSha256"]) == 64
    assert report["provenance"]["sourceRefs"] == ["demo:TASK-001", "demo:TASK-002"]
    assert "接口对接提效基准" in dashboard
    assert "10.6667×" in dashboard


def test_benchmark_cli_rejects_claim_threshold_overrides(tmp_path: Path) -> None:
    source = tmp_path / "benchmark.csv"
    _write_csv(source, [_benchmark_row(sample_type="real")])

    completed = subprocess.run(
        [
            sys.executable,
            "-m",
            "skill_evolution.cli",
            "benchmark",
            "--input",
            str(source),
            "--project-root",
            str(tmp_path),
            "--minimum-real-tasks",
            "1",
            "--target-speedup",
            "0",
        ],
        check=False,
        capture_output=True,
        text=True,
        env={**os.environ, "PYTHONPATH": str(Path.cwd() / "src")},
    )

    assert completed.returncode == 2
    assert "unrecognized arguments" in completed.stderr
    assert "Traceback" not in completed.stderr


def test_benchmark_metrics_exclude_synthetic_rows_when_real_evidence_exists(
    tmp_path: Path,
) -> None:
    source = tmp_path / "benchmark.csv"
    _write_csv(
        source,
        [
            _benchmark_row(sample_type="real", source_ref="ticket:REAL-001"),
            _benchmark_row(
                task_id="DEMO-001",
                traditional_minutes=1,
                skill_minutes=1000,
                source_ref="demo:DEMO-001",
            ),
        ],
    )

    completed = subprocess.run(
        [
            sys.executable,
            "-m",
            "skill_evolution.cli",
            "benchmark",
            "--input",
            str(source),
            "--project-root",
            str(tmp_path),
            "--name",
            "real-scope",
        ],
        check=False,
        capture_output=True,
        text=True,
        env={**os.environ, "PYTHONPATH": str(Path.cwd() / "src")},
    )

    assert completed.returncode == 0, completed.stderr
    report = json.loads((tmp_path / "outputs/benchmarks/real-scope.json").read_text("utf-8"))
    assert report["dataset"]["metricScope"] == "real"
    assert report["dataset"]["metricTaskCount"] == 1
    assert report["durationMinutes"]["totalSpeedup"] == 10.0
    assert report["claim"]["status"] == "insufficient-real-samples"
