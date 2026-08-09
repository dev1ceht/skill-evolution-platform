from pathlib import Path

import pytest

from skill_evolution.evolution import EvolutionService
from skill_evolution.repository import SQLiteRepository


@pytest.fixture
def skill_file(tmp_path: Path) -> Path:
    path = tmp_path / "skills" / "frontend-api-integration" / "SKILL.md"
    path.parent.mkdir(parents=True)
    path.write_text(
        """---
name: frontend-api-integration
description: Integrate frontend APIs from OpenAPI contracts.
---

# Workflow

## Pagination

Detect pagination fields before generating page state.

## Learned rules
""",
        encoding="utf-8",
    )
    return path


def test_feedback_is_staged_evaluated_promoted_and_rolled_back(
    tmp_path: Path, skill_file: Path
) -> None:
    repository = SQLiteRepository(tmp_path / "state.db")
    service = EvolutionService(repository, skill_file)
    baseline = skill_file.read_text(encoding="utf-8")

    episode = service.open_episode(
        task="Generate the user list page integration",
        skill_name="frontend-api-integration",
        skill_version="1.0.0",
        output_summary="Generated a page-number pagination hook",
    )
    candidate = service.capture_feedback(
        episode["id"],
        "分页其实是 cursor 模式，生成前应该根据接口 Schema 判断，无法判断时请求确认。",
    )

    assert candidate["sourceEpisodeId"] == episode["id"]
    assert candidate["decision"] == "merge"
    assert candidate["status"] == "staged"
    assert skill_file.read_text(encoding="utf-8") == baseline

    evaluation = service.evaluate_candidate(candidate["id"])
    assert evaluation["passed"] is True
    assert evaluation["judge"] == "deterministic-v1"
    assert evaluation["replayCaseId"]

    version = service.promote_candidate(candidate["id"])
    promoted = skill_file.read_text(encoding="utf-8")
    assert version["version"] == "1.0.1"
    assert "cursor 模式" in promoted
    assert promoted != baseline

    rollback = service.rollback(version["id"])
    assert rollback["status"] == "rolled_back"
    assert skill_file.read_text(encoding="utf-8") == baseline

    audit_actions = [event["action"] for event in repository.list_audit_events()]
    assert audit_actions == [
        "episode.opened",
        "feedback.captured",
        "candidate.staged",
        "candidate.evaluated",
        "candidate.promoted",
        "version.rolled_back",
    ]


def test_duplicate_feedback_is_discarded(tmp_path: Path, skill_file: Path) -> None:
    repository = SQLiteRepository(tmp_path / "state.db")
    service = EvolutionService(repository, skill_file)
    episode = service.open_episode("Pagination", "frontend-api-integration", "1.0.0", "ok")

    candidate = service.capture_feedback(
        episode["id"], "Detect pagination fields before generating page state."
    )

    assert candidate["decision"] == "discard"
    assert candidate["status"] == "discarded"
