from __future__ import annotations

import hashlib
import re
from pathlib import Path
from typing import Any

from . import contracts as contracts_module
from .contracts import _ts_identifier, _ts_string, build_integration
from .evolution import DeterministicJudge


class ContractReplayJudge:
    """Offline judge that replays a real OpenAPI contract through code generation.

    A replay artifact records the historical baseline contract separately from
    the contract revision used for the current candidate replay. This lets an
    API evolve without rewriting the original failure evidence.
    """

    name = "contract-replay-v1"

    BEHAVIOR_CHECKS = (
        "contractSourceMatched",
        "contractGenerated",
        "parameterReferencesResolved",
        "pathParametersEncoded",
        "headerNamesPreserved",
    )

    def __init__(
        self,
        contract: str | dict[str, Any],
        *,
        page_name: str,
        baseline_artifact: dict[str, Any] | None = None,
    ) -> None:
        self.contract = contract
        self.page_name = page_name
        self.structural_judge = DeterministicJudge()
        self.baseline_artifact = baseline_artifact
        self.candidate_evidence: dict[str, str] = {}
        if baseline_artifact is not None:
            checks = baseline_artifact.get("checks", {})
            if not set(self.BEHAVIOR_CHECKS).issubset(checks):
                raise ValueError("Baseline artifact is missing contract replay checks")

    def provenance(self) -> dict[str, Any]:
        if self.baseline_artifact is None:
            return {"pageName": self.page_name}
        replay_source = self.baseline_artifact.get("replaySource", {})
        provenance = {
            "pageName": self.page_name,
            "baselineArtifactVersion": self.baseline_artifact.get("artifactVersion"),
            "baselineSourceCommit": self.baseline_artifact.get("sourceCommit"),
            "baselineContractHash": self.baseline_artifact.get("contractHash"),
            "replaySourceCommit": replay_source.get("sourceCommit"),
            "replayContractHash": replay_source.get("contractHash"),
            "baselineFailure": self.baseline_artifact.get("failure"),
        }
        provenance["candidateEvidence"] = self.candidate_evidence
        return provenance

    def evaluate(self, content: str, proposed_rule: str) -> dict[str, bool]:
        checks = self.structural_judge.evaluate(content, proposed_rule)
        if self.baseline_artifact is not None and proposed_rule not in content:
            checks.update(
                {
                    name: bool(self.baseline_artifact["checks"][name])
                    for name in self.BEHAVIOR_CHECKS
                }
            )
            return checks
        try:
            result = build_integration(self.contract, self.page_name)
            client = result["typescriptClient"]
            contract_tests = result["contractTests"]
            operations = result["operations"]
            self.candidate_evidence = {
                "generatorSourceHash": hashlib.sha256(
                    Path(contracts_module.__file__).read_bytes()
                ).hexdigest(),
                "generatedClientHash": hashlib.sha256(client.encode("utf-8")).hexdigest(),
                "generatedContractTestsHash": hashlib.sha256(
                    contract_tests.encode("utf-8")
                ).hexdigest(),
            }
            replay_source = (
                self.baseline_artifact.get("replaySource", {})
                if self.baseline_artifact
                else {}
            )
            expected_contract_hash = replay_source.get(
                "contractHash",
                self.baseline_artifact.get("contractHash") if self.baseline_artifact else None,
            )
            checks.update(
                {
                    "contractGenerated": bool(operations),
                    "contractSourceMatched": self.baseline_artifact is None
                    or result["documentHash"] == expected_contract_hash,
                    "parameterReferencesResolved": self._parameters_resolved(operations),
                    "pathParametersEncoded": self._paths_encoded(
                        operations, client, contract_tests
                    ),
                    "headerNamesPreserved": self._headers_preserved(operations, client),
                }
            )
        except (KeyError, TypeError, ValueError):
            checks.update(
                {
                    "contractGenerated": False,
                    "contractSourceMatched": False,
                    "parameterReferencesResolved": False,
                    "pathParametersEncoded": False,
                    "headerNamesPreserved": False,
                }
            )
        return checks

    @staticmethod
    def _parameters_resolved(operations: list[dict[str, Any]]) -> bool:
        return all(
            "$ref" not in parameter and "name" in parameter
            for operation in operations
            for parameter in operation.get("parameters", [])
        )

    @staticmethod
    def _paths_encoded(
        operations: list[dict[str, Any]], client: str, contract_tests: str
    ) -> bool:
        for operation in operations:
            expected_path = operation["path"]
            for parameter in operation.get("parameters", []):
                if parameter.get("in") != "path":
                    continue
                identifier = _ts_identifier(parameter["name"])
                if f"encodeURIComponent(String({identifier}))" not in client:
                    return False
                expected_path = expected_path.replace(
                    f"{{{parameter['name']}}}", "fixture"
                )
            if "{" in expected_path or (
                f"toBe({_ts_string(expected_path)})" not in contract_tests
            ):
                return False
        return True

    @staticmethod
    def _headers_preserved(operations: list[dict[str, Any]], client: str) -> bool:
        for operation in operations:
            for parameter in operation.get("parameters", []):
                if parameter.get("in") != "header":
                    continue
                name = parameter["name"]
                identifier = _ts_identifier(name)
                assignment = re.compile(
                    rf"\w+\[{re.escape(_ts_string(name))}\] = String\({identifier}\)"
                )
                if assignment.search(client) is None:
                    return False
        return True
