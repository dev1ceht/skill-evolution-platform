from __future__ import annotations

import json
import re
import uuid
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from .contracts import build_integration
from .evolution import EvolutionService
from .repository import SQLiteRepository


class ApiApplication:
    """Framework-independent JSON API used by tests and the HTTP adapter."""

    def __init__(self, repository: SQLiteRepository, skill_file: str | Path) -> None:
        self.repository = repository
        self.evolution = EvolutionService(repository, skill_file)

    def dispatch(
        self, method: str, path: str, body: dict[str, Any] | None
    ) -> tuple[int, dict[str, Any] | list[dict[str, Any]]]:
        try:
            return self._dispatch(method.upper(), path.rstrip("/") or "/", body or {})
        except KeyError as error:
            return 404, {"error": str(error)}
        except (TypeError, ValueError) as error:
            return 400, {"error": str(error)}

    def _dispatch(
        self, method: str, path: str, body: dict[str, Any]
    ) -> tuple[int, dict[str, Any] | list[dict[str, Any]]]:
        if method == "GET" and path == "/api/health":
            return 200, {"status": "ok", "service": "skill-evolution-platform"}
        if method == "GET" and path == "/api/dashboard":
            return 200, self._dashboard()
        if method == "GET" and path == "/api/candidates":
            return 200, [self.evolution._candidate_view(item) for item in self.repository.list("candidates")]
        if method == "GET" and path == "/api/evaluations":
            return 200, [self._evaluation_view(item) for item in self.repository.list("evaluations")]
        if method == "GET" and path == "/api/versions":
            return 200, self.repository.list("versions")
        if method == "GET" and path == "/api/audit":
            return 200, self.repository.list_audit_events()
        if method == "POST" and path == "/api/integrations":
            result = build_integration(body["document"], body.get("pageName", "GeneratedPage"))
            record = {
                "id": f"integration_{uuid.uuid4().hex[:12]}",
                "page_name": body.get("pageName", "GeneratedPage"),
                "document_hash": result["documentHash"],
                "operation_count": len(result["operations"]),
                "result_json": json.dumps(result, ensure_ascii=False),
                "created_at": datetime.now(UTC).isoformat(),
            }
            self.repository.insert("integrations", record)
            return 201, {"id": record["id"], **result}
        if method == "POST" and path == "/api/episodes":
            return 201, self.evolution.open_episode(
                body["task"],
                body.get("skillName", "frontend-api-integration"),
                body.get("skillVersion", "1.0.0"),
                body.get("outputSummary", ""),
            )
        feedback_match = re.fullmatch(r"/api/episodes/([^/]+)/feedback", path)
        if method == "POST" and feedback_match:
            return 201, self.evolution.capture_feedback(feedback_match.group(1), body["feedback"])
        evaluate_match = re.fullmatch(r"/api/candidates/([^/]+)/evaluate", path)
        if method == "POST" and evaluate_match:
            return 200, self.evolution.evaluate_candidate(evaluate_match.group(1))
        promote_match = re.fullmatch(r"/api/candidates/([^/]+)/promote", path)
        if method == "POST" and promote_match:
            return 200, self.evolution.promote_candidate(promote_match.group(1))
        rollback_match = re.fullmatch(r"/api/versions/([^/]+)/rollback", path)
        if method == "POST" and rollback_match:
            return 200, self.evolution.rollback(rollback_match.group(1))
        return 404, {"error": f"Route not found: {method} {path}"}

    def _dashboard(self) -> dict[str, Any]:
        candidates = self.repository.list("candidates")
        versions = self.repository.list("versions")
        return {
            "metrics": {
                "integrations": self.repository.count("integrations"),
                "episodes": self.repository.count("episodes"),
                "candidates": len(candidates),
                "promoted": sum(item["status"] == "promoted" for item in candidates),
                "versions": len(versions),
            },
            "decisionBreakdown": {
                decision: sum(item["decision"] == decision for item in candidates)
                for decision in ("add", "merge", "discard", "pending")
            },
            "recentCandidates": [self.evolution._candidate_view(item) for item in candidates[-8:]],
            "recentAudit": self.repository.list_audit_events(limit=12)[-12:],
        }

    @staticmethod
    def _evaluation_view(item: dict[str, Any]) -> dict[str, Any]:
        return {
            "id": item["id"],
            "candidateId": item["candidate_id"],
            "replayCaseId": item["replay_case_id"],
            "judge": item["judge"],
            "passed": bool(item["passed"]),
            "baseline": json.loads(item["baseline_json"]),
            "candidate": json.loads(item["candidate_json"]),
            "comparison": json.loads(item["comparison_json"]),
            "createdAt": item["created_at"],
        }
