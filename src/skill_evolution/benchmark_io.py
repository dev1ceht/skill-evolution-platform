from __future__ import annotations

import csv
import hashlib
import io
import json
import math
import os
import re
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .benchmark import BenchmarkTask, calculate_benchmark_report
from .benchmark_dashboard import render_benchmark_dashboard


REQUIRED_FIELDS = (
    "task_id",
    "task_name",
    "project",
    "framework",
    "operation_count",
    "traditional_minutes",
    "skill_minutes",
    "traditional_rework_count",
    "skill_rework_count",
    "traditional_defect_count",
    "skill_defect_count",
    "traditional_first_pass",
    "skill_first_pass",
    "skill_token_count",
    "sample_type",
    "recorded_at",
    "source_ref",
)
REPORT_NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")


def _number(row: dict[str, Any], field: str, *, integer: bool = False) -> float | int:
    value = row.get(field)
    if isinstance(value, bool):
        raise ValueError(f"{field} must be a number")
    try:
        numeric = float(value)
    except (TypeError, ValueError) as error:
        raise ValueError(f"{field} must be a number") from error
    if not math.isfinite(numeric):
        raise ValueError(f"{field} must be finite")
    if numeric < 0:
        raise ValueError(f"{field} must be non-negative")
    if integer:
        if not numeric.is_integer():
            raise ValueError(f"{field} must be an integer")
        return int(numeric)
    return numeric


def _boolean(row: dict[str, Any], field: str) -> bool:
    value = row.get(field)
    if isinstance(value, bool):
        return value
    normalized = str(value).strip().lower()
    if normalized in {"true", "1", "yes"}:
        return True
    if normalized in {"false", "0", "no"}:
        return False
    raise ValueError(f"{field} must be true or false")


def _text(row: dict[str, Any], field: str, *, row_number: int) -> str:
    value = str(row.get(field, "")).strip()
    if not value:
        raise ValueError(f"row {row_number}: {field} is required")
    return value


def _read_rows(source: Path, payload: bytes) -> list[dict[str, Any]]:
    try:
        text = payload.decode("utf-8-sig")
    except UnicodeDecodeError as error:
        raise ValueError("benchmark input must use UTF-8 encoding") from error
    if source.suffix.lower() == ".csv":
        return list(csv.DictReader(io.StringIO(text, newline="")))
    if source.suffix.lower() == ".json":
        value = json.loads(text)
        if not isinstance(value, list) or not all(isinstance(item, dict) for item in value):
            raise ValueError("JSON benchmark input must be an array of objects")
        return value
    raise ValueError("benchmark input must be a .csv or .json file")


def load_benchmark_tasks(source: Path) -> tuple[list[BenchmarkTask], bytes, Path]:
    resolved = source.resolve(strict=True)
    payload = resolved.read_bytes()
    raw_rows = _read_rows(resolved, payload)
    if not raw_rows:
        raise ValueError("benchmark input must contain at least one task")

    tasks: list[BenchmarkTask] = []
    task_ids: set[str] = set()
    for row_number, raw in enumerate(raw_rows, start=2):
        missing = [field for field in REQUIRED_FIELDS if field not in raw]
        if missing:
            raise ValueError(
                f"row {row_number}: benchmark input is missing fields: {', '.join(missing)}"
            )
        task_id = _text(raw, "task_id", row_number=row_number)
        if task_id in task_ids:
            raise ValueError(f"row {row_number}: duplicate task_id {task_id}")
        task_ids.add(task_id)

        sample_type = _text(raw, "sample_type", row_number=row_number).lower()
        if sample_type not in {"real", "synthetic"}:
            raise ValueError(f"row {row_number}: sample_type must be real or synthetic")
        recorded_at = _text(raw, "recorded_at", row_number=row_number)
        try:
            timestamp = datetime.fromisoformat(recorded_at.replace("Z", "+00:00"))
        except ValueError as error:
            raise ValueError(f"row {row_number}: recorded_at must be ISO 8601") from error
        if timestamp.tzinfo is None:
            raise ValueError(f"row {row_number}: recorded_at must include a timezone")

        traditional_minutes = float(_number(raw, "traditional_minutes"))
        skill_minutes = float(_number(raw, "skill_minutes"))
        if traditional_minutes <= 0 or skill_minutes <= 0:
            raise ValueError(f"row {row_number}: task durations must be greater than zero")
        operation_count = int(_number(raw, "operation_count", integer=True))
        if operation_count <= 0:
            raise ValueError(f"row {row_number}: operation_count must be greater than zero")

        tasks.append(
            BenchmarkTask(
                taskId=task_id,
                taskName=_text(raw, "task_name", row_number=row_number),
                project=_text(raw, "project", row_number=row_number),
                framework=_text(raw, "framework", row_number=row_number).lower(),
                operationCount=operation_count,
                traditionalMinutes=traditional_minutes,
                skillMinutes=skill_minutes,
                traditionalReworkCount=int(
                    _number(raw, "traditional_rework_count", integer=True)
                ),
                skillReworkCount=int(_number(raw, "skill_rework_count", integer=True)),
                traditionalDefectCount=int(
                    _number(raw, "traditional_defect_count", integer=True)
                ),
                skillDefectCount=int(_number(raw, "skill_defect_count", integer=True)),
                traditionalFirstPass=_boolean(raw, "traditional_first_pass"),
                skillFirstPass=_boolean(raw, "skill_first_pass"),
                skillTokenCount=int(_number(raw, "skill_token_count", integer=True)),
                sampleType=sample_type,
                recordedAt=timestamp.isoformat(),
                sourceRef=_text(raw, "source_ref", row_number=row_number),
            )
        )
    return tasks, payload, resolved


def _atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temp_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp_name, path)
    except BaseException:
        Path(temp_name).unlink(missing_ok=True)
        raise


def write_benchmark_report(source: Path, project_root: Path, name: str) -> dict[str, str]:
    if not REPORT_NAME.fullmatch(name):
        raise ValueError(
            "report name must contain only letters, numbers, underscores, and hyphens"
        )
    tasks, payload, resolved_source = load_benchmark_tasks(source)
    report = calculate_benchmark_report(
        tasks,
        generated_at=datetime.now(timezone.utc),
        input_path=str(resolved_source),
        input_sha256=hashlib.sha256(payload).hexdigest(),
    )

    output = project_root.resolve() / "outputs" / "benchmarks"
    report_path = output / f"{name}.json"
    dashboard_path = output / f"{name}.html"
    _atomic_write(report_path, json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    _atomic_write(dashboard_path, render_benchmark_dashboard(report))
    return {"report": str(report_path), "dashboard": str(dashboard_path)}
