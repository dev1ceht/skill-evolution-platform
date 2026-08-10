from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime
from statistics import mean
from typing import Callable, Sequence


MINIMUM_REAL_TASKS = 20
TARGET_SPEEDUP = 20.0


@dataclass(frozen=True)
class BenchmarkTask:
    taskId: str
    taskName: str
    project: str
    framework: str
    operationCount: int
    traditionalMinutes: float
    skillMinutes: float
    traditionalReworkCount: int
    skillReworkCount: int
    traditionalDefectCount: int
    skillDefectCount: int
    traditionalFirstPass: bool
    skillFirstPass: bool
    skillTokenCount: int
    sampleType: str
    recordedAt: str
    sourceRef: str

    def as_report_record(self) -> dict[str, object]:
        return asdict(self)


def _percentile(values: Sequence[float | int], percentile: float) -> float:
    ordered = sorted(float(value) for value in values)
    if len(ordered) == 1:
        return round(ordered[0], 4)
    position = (len(ordered) - 1) * percentile
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    interpolated = ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)
    return round(interpolated, 4)


def _rate(tasks: Sequence[BenchmarkTask], selector: Callable[[BenchmarkTask], bool]) -> float:
    return round(sum(1 for task in tasks if selector(task)) / len(tasks), 4)


def _average(
    tasks: Sequence[BenchmarkTask], selector: Callable[[BenchmarkTask], int]
) -> float:
    return round(mean(selector(task) for task in tasks), 4)


def calculate_benchmark_report(
    tasks: Sequence[BenchmarkTask],
    *,
    generated_at: datetime,
    input_path: str,
    input_sha256: str,
) -> dict[str, object]:
    """Calculate a deterministic report from already-ingested task evidence."""
    if not tasks:
        raise ValueError("benchmark input must contain at least one task")

    real_tasks = [task for task in tasks if task.sampleType == "real"]
    metric_tasks = real_tasks or list(tasks)
    metric_scope = "real" if real_tasks else "synthetic-demo"
    traditional = [task.traditionalMinutes for task in metric_tasks]
    skill = [task.skillMinutes for task in metric_tasks]
    paired_speedup = [before / after for before, after in zip(traditional, skill, strict=True)]
    traditional_total = sum(traditional)
    skill_total = sum(skill)
    total_speedup = traditional_total / skill_total

    if len(real_tasks) < MINIMUM_REAL_TASKS:
        claim_status = "insufficient-real-samples"
        supported = False
    else:
        supported = total_speedup >= TARGET_SPEEDUP
        claim_status = "supported" if supported else "not-supported"

    return {
        "schemaVersion": "1.0",
        "generatedAt": generated_at.isoformat(),
        "dataset": {
            "taskCount": len(tasks),
            "realTaskCount": len(real_tasks),
            "syntheticTaskCount": len(tasks) - len(real_tasks),
            "operationCount": sum(task.operationCount for task in tasks),
            "metricScope": metric_scope,
            "metricTaskCount": len(metric_tasks),
        },
        "durationMinutes": {
            "traditionalP50": _percentile(traditional, 0.5),
            "traditionalP90": _percentile(traditional, 0.9),
            "skillP50": _percentile(skill, 0.5),
            "skillP90": _percentile(skill, 0.9),
            "traditionalTotal": round(traditional_total, 4),
            "skillTotal": round(skill_total, 4),
            "totalSpeedup": round(total_speedup, 4),
            "pairedSpeedupP50": _percentile(paired_speedup, 0.5),
            "pairedSpeedupP90": _percentile(paired_speedup, 0.9),
        },
        "quality": {
            "traditionalFirstPassRate": _rate(
                metric_tasks, lambda task: task.traditionalFirstPass
            ),
            "skillFirstPassRate": _rate(metric_tasks, lambda task: task.skillFirstPass),
            "traditionalReworkPerTask": _average(
                metric_tasks, lambda task: task.traditionalReworkCount
            ),
            "skillReworkPerTask": _average(
                metric_tasks, lambda task: task.skillReworkCount
            ),
            "traditionalDefectsPerTask": _average(
                metric_tasks, lambda task: task.traditionalDefectCount
            ),
            "skillDefectsPerTask": _average(
                metric_tasks, lambda task: task.skillDefectCount
            ),
        },
        "tokens": {
            "skillTotal": sum(task.skillTokenCount for task in metric_tasks),
            "skillP50": _percentile([task.skillTokenCount for task in metric_tasks], 0.5),
            "skillP90": _percentile([task.skillTokenCount for task in metric_tasks], 0.9),
        },
        "claim": {
            "minimumRealTasks": MINIMUM_REAL_TASKS,
            "targetSpeedup": TARGET_SPEEDUP,
            "status": claim_status,
            "supported": supported,
        },
        "provenance": {
            "inputPath": input_path,
            "inputSha256": input_sha256,
            "sourceRefs": sorted({task.sourceRef for task in tasks}),
        },
        "tasks": [task.as_report_record() for task in tasks],
    }
