from __future__ import annotations

import hashlib
import json
import os
import re
import uuid
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

from .ports import JudgePort, RepositoryPort


ID_PATTERN = re.compile(r"^[a-z]+_[0-9a-f]{12}$")
STRUCTURAL_CHECKS = (
    "frontmatterPreserved",
    "skillSizeWithinLimit",
    "noTodoPlaceholder",
)


def _now() -> str:
    return datetime.now(UTC).isoformat()


def _id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex[:12]}"


def _hash(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


def _validate_id(value: str, prefix: str) -> None:
    if not ID_PATTERN.fullmatch(value) or not value.startswith(f"{prefix}_"):
        raise ValueError(f"Invalid {prefix} identifier")


def _normalized(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip()).casefold()


def _tokens(value: str) -> set[str]:
    latin = set(re.findall(r"[a-z0-9_-]+", value.casefold()))
    chinese = "".join(re.findall(r"[\u4e00-\u9fff]", value))
    grams = {chinese[index : index + 2] for index in range(max(0, len(chinese) - 1))}
    aliases = set()
    for marker, alias in (("分页", "pagination"), ("接口", "api"), ("模式", "mode"), ("版本", "version"), ("测试", "test")):
        if marker in value:
            aliases.add(alias)
    return latin | grams | aliases


def _similarity(left_text: str, right_text: str) -> float:
    left = _tokens(left_text)
    right = _tokens(right_text)
    return len(left & right) / len(left) if left and right else 0.0


def _retrieve_rules(feedback: str, content: str, limit: int = 3) -> list[dict[str, Any]]:
    section = "root"
    matches = []
    for line_number, raw_line in enumerate(content.splitlines(), start=1):
        line = raw_line.strip()
        if line.startswith("## "):
            section = line[3:]
            continue
        if not line or line in {"---"} or line.startswith("#"):
            continue
        score = _similarity(feedback, line)
        if score:
            matches.append(
                {"section": section, "line": line_number, "text": line, "score": round(score, 4)}
            )
    return sorted(matches, key=lambda item: item["score"], reverse=True)[:limit]


def _feedback_confidence(feedback: str) -> float:
    corrective_markers = (
        "应该",
        "其实",
        "错误",
        "不对",
        "改为",
        "需要",
        "should",
        "must",
        "incorrect",
        "instead",
    )
    normalized = feedback.casefold()
    if any(marker in normalized for marker in corrective_markers):
        return 0.9
    if len(feedback.strip()) >= 24:
        return 0.6
    return 0.3


def _next_patch_version(version: str) -> str:
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", version)
    if not match:
        return "0.0.1"
    major, minor, patch = map(int, match.groups())
    return f"{major}.{minor}.{patch + 1}"


class DeterministicJudge:
    name = "deterministic-v2"

    def evaluate(self, content: str, proposed_rule: str) -> dict[str, bool]:
        return {
            "frontmatterPreserved": content.startswith("---\n") and "\n---\n" in content[4:],
            "candidateRulePresent": proposed_rule in content,
            "skillSizeWithinLimit": len(content.splitlines()) <= 500,
            "noTodoPlaceholder": "TODO" not in content,
        }


class EvolutionService:
    def __init__(
        self,
        repository: RepositoryPort,
        skill_file: str | Path,
        *,
        skill_root: str | Path | None = None,
        judge: JudgePort | None = None,
    ) -> None:
        self.repository = repository
        self.skill_file = Path(skill_file).resolve()
        self.skill_root = Path(skill_root).resolve() if skill_root else self.skill_file.parents[1]
        if not self.skill_file.is_relative_to(self.skill_root):
            raise ValueError("Skill file must be inside the configured Skill root")
        self.judge = judge or DeterministicJudge()

    def _audit(
        self,
        action: str,
        entity_type: str,
        entity_id: str,
        payload: dict[str, Any] | None = None,
    ) -> None:
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
        if not task.strip() or ttl_hours <= 0:
            raise ValueError("Task is required and ttl_hours must be positive")
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
        _validate_id(episode_id, "episode")
        episode = self.repository.get("episodes", episode_id)
        if not episode:
            raise KeyError(f"Unknown episode: {episode_id}")
        if episode["status"] != "awaiting_feedback":
            raise ValueError("Episode is no longer accepting feedback")
        if datetime.fromisoformat(episode["expires_at"]) <= datetime.now(UTC):
            raise ValueError("Pending window has expired")
        if not feedback.strip():
            raise ValueError("Feedback cannot be empty")

        self._audit("feedback.captured", "episode", episode_id, {"feedback": feedback})
        content = self.skill_file.read_text(encoding="utf-8")
        retrieved = _retrieve_rules(feedback, content)
        similarity = retrieved[0]["score"] if retrieved else 0.0
        confidence = _feedback_confidence(feedback)
        normalized_feedback = _normalized(feedback).rstrip("。.！!")
        duplicate = normalized_feedback in _normalized(content) or similarity >= 0.95
        topic_overlap = bool(
            _tokens(feedback)
            & _tokens(content)
            & {"pagination", "api", "schema", "version", "test"}
        )

        if duplicate:
            decision, status, staged_content = "discard", "discarded", None
        elif confidence < 0.45:
            decision, status, staged_content = "pending", "pending", None
        elif similarity >= 0.08 or topic_overlap:
            decision, status = "merge", "staged"
            staged_content = self._stage_rule(content, feedback, decision)
        else:
            decision, status = "add", "staged"
            staged_content = self._stage_rule(content, feedback, decision)

        candidate = {
            "id": _id("candidate"),
            "source_episode_id": episode_id,
            "feedback": feedback,
            "proposed_rule": feedback.strip(),
            "decision": decision,
            "status": status,
            "similarity": similarity,
            "confidence": confidence,
            "retrieved_rules_json": json.dumps(retrieved, ensure_ascii=False),
            "base_content_hash": _hash(content),
            "staged_content": staged_content,
            "evaluation_id": None,
            "created_at": _now(),
        }
        self.repository.insert("candidates", candidate)
        self._audit(f"candidate.{status}", "candidate", candidate["id"], {"decision": decision})
        self.repository.update("episodes", episode_id, {"status": "feedback_received"})
        self._audit(
            "episode.state_changed",
            "episode",
            episode_id,
            {"from": "awaiting_feedback", "to": "feedback_received"},
        )
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
            "confidence": record["confidence"],
            "retrievedRules": json.loads(record["retrieved_rules_json"]),
        }

    def evaluate_candidate(self, candidate_id: str) -> dict[str, Any]:
        _validate_id(candidate_id, "candidate")
        candidate = self.repository.get("candidates", candidate_id)
        if not candidate:
            raise KeyError(f"Unknown candidate: {candidate_id}")
        if candidate["status"] != "staged":
            raise ValueError("Only staged candidates can be evaluated")

        baseline = self.skill_file.read_text(encoding="utf-8")
        if _hash(baseline) != candidate["base_content_hash"]:
            raise ValueError("Skill changed after staging; create a fresh candidate")
        staged = candidate["staged_content"] or ""
        baseline_checks = self.judge.evaluate(baseline, candidate["proposed_rule"])
        candidate_checks = self.judge.evaluate(staged, candidate["proposed_rule"])
        regressions = [
            key for key in STRUCTURAL_CHECKS if baseline_checks[key] and not candidate_checks[key]
        ]
        improvements = []
        if not baseline_checks["candidateRulePresent"] and candidate_checks["candidateRulePresent"]:
            improvements.append("candidateRulePresent")
        passed = (
            all(candidate_checks[key] for key in STRUCTURAL_CHECKS)
            and candidate_checks["candidateRulePresent"]
            and not regressions
        )
        comparison = {"improvements": improvements, "regressions": regressions}

        replay_id = _id("replay")
        self.repository.insert(
            "replay_cases",
            {
                "id": replay_id,
                "candidate_id": candidate_id,
                "source_episode_id": candidate["source_episode_id"],
                "input_json": json.dumps({"feedback": candidate["feedback"]}, ensure_ascii=False),
                "expected_json": json.dumps({"rulePresent": True}, ensure_ascii=False),
                "created_at": _now(),
            },
        )
        evaluation = {
            "id": _id("evaluation"),
            "candidate_id": candidate_id,
            "replay_case_id": replay_id,
            "judge": self.judge.name,
            "passed": int(passed),
            "baseline_json": json.dumps(baseline_checks, ensure_ascii=False),
            "candidate_json": json.dumps(candidate_checks, ensure_ascii=False),
            "comparison_json": json.dumps(comparison, ensure_ascii=False),
            "created_at": _now(),
        }
        self.repository.insert("evaluations", evaluation)
        self.repository.update(
            "candidates", candidate_id, {"status": "evaluated", "evaluation_id": evaluation["id"]}
        )
        self._audit("candidate.evaluated", "candidate", candidate_id, {"passed": passed})
        return {
            "id": evaluation["id"],
            "candidateId": candidate_id,
            "replayCaseId": replay_id,
            "judge": self.judge.name,
            "passed": passed,
            "checks": candidate_checks,
            "baselineChecks": baseline_checks,
            "comparison": comparison,
        }

    def promote_candidate(self, candidate_id: str) -> dict[str, Any]:
        _validate_id(candidate_id, "candidate")
        candidate = self.repository.get("candidates", candidate_id)
        if not candidate or candidate["status"] != "evaluated" or not candidate["evaluation_id"]:
            raise ValueError("Candidate must be evaluated exactly once before promotion")
        evaluation = self.repository.get("evaluations", candidate["evaluation_id"])
        if not evaluation or not evaluation["passed"]:
            raise ValueError("Candidate evaluation did not pass")
        episode = self.repository.get("episodes", candidate["source_episode_id"])
        before = self.skill_file.read_text(encoding="utf-8")
        if _hash(before) != candidate["base_content_hash"]:
            raise ValueError("Skill changed after staging; promotion would overwrite a newer version")
        head = self.repository.get_skill_head(episode["skill_name"])
        base_version = head["version"] if head else episode["skill_version"]
        return self._publish_version(
            candidate,
            episode["skill_name"],
            _next_patch_version(base_version),
            before,
            candidate["staged_content"],
            parent_version_id=head["version_id"] if head else None,
            status="promotion",
        )

    def rollback(self, version_id: str) -> dict[str, Any]:
        _validate_id(version_id, "version")
        target = self.repository.get("versions", version_id)
        if not target:
            raise KeyError(f"Unknown version: {version_id}")
        head = self.repository.get_skill_head(target["skill_name"])
        if not head or head["version_id"] != version_id:
            raise ValueError("Only the active version can be rolled back safely")
        before = self.skill_file.read_text(encoding="utf-8")
        if _hash(before) != head["content_hash"]:
            raise ValueError("Skill file differs from the active version; rollback aborted")
        candidate = self.repository.get("candidates", target["candidate_id"])
        result = self._publish_version(
            candidate,
            target["skill_name"],
            _next_patch_version(target["version"]),
            before,
            target["before_content"],
            parent_version_id=version_id,
            status="rollback",
        )
        self._audit("version.rolled_back", "version", version_id, {"rollbackVersionId": result["id"]})
        result["status"] = "rolled_back"
        return result

    def _publish_version(
        self,
        candidate: dict[str, Any],
        skill_name: str,
        version_number: str,
        before: str,
        after: str,
        *,
        parent_version_id: str | None,
        status: str,
    ) -> dict[str, Any]:
        intent = {
            "id": _id("intent"),
            "candidate_id": candidate["id"],
            "skill_name": skill_name,
            "target_version": version_number,
            "base_content_hash": _hash(before),
            "before_content": before,
            "after_content": after,
            "status": "prepared",
            "created_at": _now(),
        }
        self.repository.insert("promotion_intents", intent)
        self._audit(
            "promotion_intent.prepared",
            "promotion_intent",
            intent["id"],
            {"targetVersion": version_number, "baseContentHash": intent["base_content_hash"]},
        )
        self._atomic_write(after)
        record = {
            "id": _id("version"),
            "skill_name": skill_name,
            "version": version_number,
            "candidate_id": candidate["id"],
            "parent_version_id": parent_version_id,
            "before_content": before,
            "after_content": after,
            "content_hash": _hash(after),
            "status": status,
            "created_at": _now(),
        }
        self.repository.finalize_promotion(intent["id"], record)
        self._audit(
            "promotion_intent.completed",
            "promotion_intent",
            intent["id"],
            {"versionId": record["id"]},
        )
        self._audit(
            "skill_head.changed",
            "skill",
            skill_name,
            {"fromVersionId": parent_version_id, "toVersionId": record["id"]},
        )
        if status == "promotion":
            self.repository.update("candidates", candidate["id"], {"status": "promoted"})
            self._audit("candidate.promoted", "candidate", candidate["id"], {"version": version_number})
        self._audit("version.activated", "version", record["id"], {"status": status})
        return {
            "id": record["id"],
            "skill_name": skill_name,
            "version": version_number,
            "content_hash": record["content_hash"],
            "status": "active",
        }

    def _atomic_write(self, content: str) -> None:
        if not self.skill_file.is_relative_to(self.skill_root):
            raise ValueError("Skill write escaped the configured Skill root")
        self.skill_file.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.skill_file.with_suffix(".md.tmp")
        temporary.write_text(content, encoding="utf-8")
        os.replace(temporary, self.skill_file)
