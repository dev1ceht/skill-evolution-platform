from __future__ import annotations

import hashlib
import json
import os
import re
import uuid
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

from .repository import SQLiteRepository


def _now() -> str:
    return datetime.now(UTC).isoformat()


def _id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex[:12]}"


def _normalized(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip()).casefold()


def _tokens(value: str) -> set[str]:
    latin = set(re.findall(r"[a-z0-9_-]+", value.casefold()))
    chinese = "".join(re.findall(r"[\u4e00-\u9fff]", value))
    grams = {chinese[index : index + 2] for index in range(max(0, len(chinese) - 1))}
    aliases = set()
    if "分页" in value:
        aliases.add("pagination")
    if "接口" in value:
        aliases.add("api")
    if "模式" in value:
        aliases.add("mode")
    return latin | grams | aliases


def _similarity(feedback: str, content: str) -> float:
    left = _tokens(feedback)
    right = _tokens(content)
    if not left or not right:
        return 0.0
    return len(left & right) / len(left)


def _next_patch_version(version: str) -> str:
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", version)
    if not match:
        return "0.0.1"
    major, minor, patch = map(int, match.groups())
    return f"{major}.{minor}.{patch + 1}"


class EvolutionService:
    def __init__(self, repository: SQLiteRepository, skill_file: str | Path) -> None:
        self.repository = repository
        self.skill_file = Path(skill_file).resolve()

    def _audit(self, action: str, entity_type: str, entity_id: str, payload: dict[str, Any] | None = None) -> None:
        self.repository.add_audit_event(
            {
                "id": _id("audit"),
                "action": action,
                "entity_type": entity_type,
                "entity_id": entity_id,
                "payload": payload or {},
                "created_at": _now(),
            }
        )

    def open_episode(
        self,
        task: str,
        skill_name: str,
        skill_version: str,
        output_summary: str,
        *,
        ttl_hours: int = 168,
    ) -> dict[str, Any]:
        opened = datetime.now(UTC)
        record = {
            "id": _id("episode"),
            "task": task,
            "skill_name": skill_name,
            "skill_version": skill_version,
            "output_summary": output_summary,
            "status": "awaiting_feedback",
            "opened_at": opened.isoformat(),
            "expires_at": (opened + timedelta(hours=ttl_hours)).isoformat(),
        }
        self.repository.insert("episodes", record)
        self._audit("episode.opened", "episode", record["id"])
        return record

    def capture_feedback(self, episode_id: str, feedback: str) -> dict[str, Any]:
        episode = self.repository.get("episodes", episode_id)
        if not episode:
            raise KeyError(f"Unknown episode: {episode_id}")
        if not feedback.strip():
            raise ValueError("Feedback cannot be empty")
        self._audit("feedback.captured", "episode", episode_id, {"feedback": feedback})
        content = self.skill_file.read_text(encoding="utf-8")
        normalized_feedback = _normalized(feedback).rstrip("。.！!")
        normalized_content = _normalized(content)
        similarity = _similarity(feedback, content)
        duplicate = normalized_feedback in normalized_content
        if duplicate or similarity >= 0.9:
            decision, status = "discard", "discarded"
            staged_content = None
        elif similarity >= 0.08 or bool(_tokens(feedback) & _tokens(content) & {"pagination", "api", "schema", "version", "test"}):
            decision, status = "merge", "staged"
            staged_content = self._stage_rule(content, feedback, "merge")
        else:
            decision, status = "add", "staged"
            staged_content = self._stage_rule(content, feedback, "add")
        candidate = {
            "id": _id("candidate"),
            "source_episode_id": episode_id,
            "feedback": feedback,
            "proposed_rule": feedback.strip(),
            "decision": decision,
            "status": status,
            "similarity": similarity,
            "staged_content": staged_content,
            "evaluation_id": None,
            "created_at": _now(),
        }
        self.repository.insert("candidates", candidate)
        self.repository.update("episodes", episode_id, {"status": "feedback_received"})
        if status == "staged":
            self._audit("candidate.staged", "candidate", candidate["id"], {"decision": decision})
        return self._candidate_view(candidate)

    @staticmethod
    def _stage_rule(content: str, rule: str, decision: str) -> str:
        entry = f"\n- [{decision}] {rule.strip()}\n"
        marker = "## Learned rules"
        if marker in content:
            return content.rstrip() + entry
        return content.rstrip() + f"\n\n{marker}\n{entry}"

    @staticmethod
    def _candidate_view(record: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": record["id"],
            "sourceEpisodeId": record["source_episode_id"],
            "feedback": record["feedback"],
            "proposedRule": record["proposed_rule"],
            "decision": record["decision"],
            "status": record["status"],
            "similarity": record["similarity"],
        }

    def evaluate_candidate(self, candidate_id: str) -> dict[str, Any]:
        candidate = self.repository.get("candidates", candidate_id)
        if not candidate:
            raise KeyError(f"Unknown candidate: {candidate_id}")
        if candidate["status"] not in {"staged", "evaluated"}:
            raise ValueError("Only staged candidates can be evaluated")
        staged = candidate["staged_content"] or ""
        replay_id = _id("replay")
        replay = {
            "id": replay_id,
            "candidate_id": candidate_id,
            "source_episode_id": candidate["source_episode_id"],
            "input_json": json.dumps({"feedback": candidate["feedback"]}, ensure_ascii=False),
            "expected_json": json.dumps({"rulePresent": True}, ensure_ascii=False),
            "created_at": _now(),
        }
        self.repository.insert("replay_cases", replay)
        checks = {
            "frontmatterPreserved": staged.startswith("---\n") and "\n---\n" in staged[4:],
            "candidateRulePresent": candidate["proposed_rule"] in staged,
            "skillSizeWithinLimit": len(staged.splitlines()) <= 500,
            "noTodoPlaceholder": "TODO" not in staged,
        }
        evaluation = {
            "id": _id("evaluation"),
            "candidate_id": candidate_id,
            "replay_case_id": replay_id,
            "judge": "deterministic-v1",
            "passed": int(all(checks.values())),
            "checks_json": json.dumps(checks, ensure_ascii=False),
            "created_at": _now(),
        }
        self.repository.insert("evaluations", evaluation)
        self.repository.update(
            "candidates",
            candidate_id,
            {"status": "evaluated", "evaluation_id": evaluation["id"]},
        )
        self._audit("candidate.evaluated", "candidate", candidate_id, {"passed": bool(evaluation["passed"])})
        return {
            "id": evaluation["id"],
            "candidateId": candidate_id,
            "replayCaseId": replay_id,
            "judge": evaluation["judge"],
            "passed": bool(evaluation["passed"]),
            "checks": checks,
        }

    def promote_candidate(self, candidate_id: str) -> dict[str, Any]:
        candidate = self.repository.get("candidates", candidate_id)
        if not candidate or not candidate.get("evaluation_id"):
            raise ValueError("Candidate must be evaluated before promotion")
        evaluation = self.repository.get("evaluations", candidate["evaluation_id"])
        if not evaluation or not evaluation["passed"]:
            raise ValueError("Candidate evaluation did not pass")
        episode = self.repository.get("episodes", candidate["source_episode_id"])
        before = self.skill_file.read_text(encoding="utf-8")
        after = candidate["staged_content"]
        self._atomic_write(after)
        record = {
            "id": _id("version"),
            "skill_name": episode["skill_name"],
            "version": _next_patch_version(episode["skill_version"]),
            "candidate_id": candidate_id,
            "before_content": before,
            "after_content": after,
            "content_hash": hashlib.sha256(after.encode("utf-8")).hexdigest(),
            "status": "active",
            "created_at": _now(),
        }
        self.repository.insert("versions", record)
        self.repository.update("candidates", candidate_id, {"status": "promoted"})
        self._audit("candidate.promoted", "candidate", candidate_id, {"version": record["version"]})
        return {key: record[key] for key in ("id", "skill_name", "version", "content_hash", "status")}

    def rollback(self, version_id: str) -> dict[str, Any]:
        version = self.repository.get("versions", version_id)
        if not version:
            raise KeyError(f"Unknown version: {version_id}")
        self._atomic_write(version["before_content"])
        self.repository.update("versions", version_id, {"status": "rolled_back"})
        self._audit("version.rolled_back", "version", version_id)
        return {"id": version_id, "status": "rolled_back", "version": version["version"]}

    def _atomic_write(self, content: str) -> None:
        self.skill_file.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.skill_file.with_suffix(".md.tmp")
        temporary.write_text(content, encoding="utf-8")
        os.replace(temporary, self.skill_file)
