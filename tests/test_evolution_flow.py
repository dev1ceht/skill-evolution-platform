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
    assert evaluation["judge"] == "deterministic-v2"
    assert evaluation["replayCaseId"]
    assert evaluation["comparison"]["regressions"] == []

    version = service.promote_candidate(candidate["id"])
    promoted = skill_file.read_text(encoding="utf-8")
    assert version["version"] == "1.0.1"
    assert "cursor 模式" in promoted
    assert promoted != baseline

    rollback = service.rollback(version["id"])
    assert rollback["status"] == "rolled_back"
    assert rollback["version"] == "1.0.2"
    assert skill_file.read_text(encoding="utf-8") == baseline
    assert repository.get("versions", version["id"])["status"] == "promotion"
    assert len(repository.list("versions")) == 2

    audit_actions = [event["action"] for event in repository.list_audit_events()]
    assert audit_actions == [
        "episode.opened",
        "feedback.captured",
        "candidate.staged",
        "episode.state_changed",
        "candidate.evaluated",
        "candidate.promoted",
        "version.activated",
        "version.activated",
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


def test_low_confidence_feedback_remains_pending(tmp_path: Path, skill_file: Path) -> None:
    repository = SQLiteRepository(tmp_path / "state.db")
    service = EvolutionService(repository, skill_file)
    episode = service.open_episode("Pagination", "frontend-api-integration", "1.0.0", "ok")

    candidate = service.capture_feedback(episode["id"], "再看看")

    assert candidate["decision"] == "pending"
    assert candidate["status"] == "pending"
    assert candidate["confidence"] < 0.45


def test_stale_candidate_cannot_overwrite_newer_skill_content(
    tmp_path: Path, skill_file: Path
) -> None:
    repository = SQLiteRepository(tmp_path / "state.db")
    service = EvolutionService(repository, skill_file)
    episode = service.open_episode("Errors", "frontend-api-integration", "1.0.0", "ok")
    candidate = service.capture_feedback(
        episode["id"], "错误码应该优先根据 response schema 映射。"
    )
    service.evaluate_candidate(candidate["id"])
    newer_content = skill_file.read_text(encoding="utf-8") + "\n- A separately promoted rule.\n"
    skill_file.write_text(newer_content, encoding="utf-8")

    with pytest.raises(ValueError, match="overwrite a newer version"):
        service.promote_candidate(candidate["id"])

    assert skill_file.read_text(encoding="utf-8") == newer_content


def test_candidate_cannot_be_promoted_twice(tmp_path: Path, skill_file: Path) -> None:
    repository = SQLiteRepository(tmp_path / "state.db")
    service = EvolutionService(repository, skill_file)
    episode = service.open_episode("Errors", "frontend-api-integration", "1.0.0", "ok")
    candidate = service.capture_feedback(
        episode["id"], "错误码应该优先根据 response schema 映射。"
    )
    service.evaluate_candidate(candidate["id"])
    service.promote_candidate(candidate["id"])

    with pytest.raises(ValueError, match="exactly once"):
        service.promote_candidate(candidate["id"])


def test_pending_window_rejects_expired_or_repeated_feedback(
    tmp_path: Path, skill_file: Path
) -> None:
    repository = SQLiteRepository(tmp_path / "state.db")
    service = EvolutionService(repository, skill_file)
    expired = service.open_episode("Expired", "frontend-api-integration", "1.0.0", "ok")
    repository.update("episodes", expired["id"], {"expires_at": "2000-01-01T00:00:00+00:00"})

    with pytest.raises(ValueError, match="expired"):
        service.capture_feedback(expired["id"], "这里应该修正。")

    active = service.open_episode("Active", "frontend-api-integration", "1.0.0", "ok")
    service.capture_feedback(active["id"], "这里应该修正。")
    with pytest.raises(ValueError, match="no longer accepting"):
        service.capture_feedback(active["id"], "再提交一次。")


def test_skill_write_must_stay_inside_configured_root(tmp_path: Path) -> None:
    repository = SQLiteRepository(tmp_path / "state.db")
    outside = tmp_path / "outside" / "SKILL.md"
    outside.parent.mkdir()
    outside.write_text("---\nname: x\ndescription: x\n---\n", encoding="utf-8")

    with pytest.raises(ValueError, match="inside the configured Skill root"):
        EvolutionService(repository, outside, skill_root=tmp_path / "skills")
