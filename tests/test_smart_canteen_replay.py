import json
from pathlib import Path

from skill_evolution.evolution import EvolutionService
from skill_evolution.replay import ContractReplayJudge
from skill_evolution.repository import SQLiteRepository


def test_smart_canteen_feedback_is_replayed_before_skill_promotion(tmp_path: Path) -> None:
    source_skill = Path("skills/frontend-api-integration/SKILL.md")
    skill_file = tmp_path / "skills" / "frontend-api-integration" / "SKILL.md"
    skill_file.parent.mkdir(parents=True)
    skill_file.write_text(source_skill.read_text(encoding="utf-8"), encoding="utf-8")
    contract = Path("examples/smart-canteen/contracts/smart-canteen.openapi.yaml").read_text(
        encoding="utf-8"
    )
    baseline_artifact = json.loads(
        Path("examples/smart-canteen/replay/baseline-generator-failure.json").read_text(
            encoding="utf-8"
        )
    )
    repository = SQLiteRepository(tmp_path / "state.db")
    judge = ContractReplayJudge(
        contract,
        page_name="MenuApprovalPage",
        baseline_artifact=baseline_artifact,
    )
    service = EvolutionService(repository, skill_file, judge=judge)
    feedback = (
        "API clients must resolve reusable parameter references, encode path parameters, "
        "and map wire-level header names to valid TypeScript identifiers."
    )

    episode = service.open_episode(
        task="Integrate the smart-canteen approval and procurement workflow",
        skill_name="frontend-api-integration",
        skill_version="1.0.0",
        output_summary="The first generated client failed on path and header parameters",
    )
    candidate = service.capture_feedback(episode["id"], feedback)
    evaluation = service.evaluate_candidate(candidate["id"])
    version = service.promote_candidate(candidate["id"])

    assert candidate["decision"] == "merge"
    assert evaluation["passed"] is True
    assert evaluation["judge"] == "contract-replay-v1"
    assert evaluation["checks"]["pathParametersEncoded"] is True
    assert evaluation["checks"]["parameterReferencesResolved"] is True
    assert evaluation["checks"]["headerNamesPreserved"] is True
    assert evaluation["checks"]["contractSourceMatched"] is True
    assert evaluation["baselineChecks"]["contractGenerated"] is False
    assert evaluation["baselineChecks"]["parameterReferencesResolved"] is False
    assert evaluation["baselineChecks"]["pathParametersEncoded"] is False
    assert evaluation["baselineChecks"]["headerNamesPreserved"] is False
    assert evaluation["baselineChecks"]["contractSourceMatched"] is True
    assert set(evaluation["comparison"]["improvements"]) >= {
        "candidateRulePresent",
        "contractGenerated",
        "parameterReferencesResolved",
        "pathParametersEncoded",
        "headerNamesPreserved",
    }
    assert version["version"] == "1.0.1"
    assert feedback in skill_file.read_text(encoding="utf-8")

    replay = repository.get("replay_cases", evaluation["replayCaseId"])
    assert replay is not None
    replay_input = json.loads(replay["input_json"])
    assert replay_input["judge"] == "contract-replay-v1"
    provenance = replay_input["judgeProvenance"]
    assert provenance["baselineSourceCommit"] == (
        "72d57ad5f2e84baebbd87fdef5be96ff9e93d81d"
    )
    assert provenance["baselineContractHash"] == (
        "e12bcf88be7dac5a8183c3338a480a9618997e4df652c6a2f40a1557fa8fd02a"
    )
    assert provenance["replaySourceCommit"] == (
        "60e703ecaa6680b0707e8c1be3a3025b156d7f46"
    )
    assert provenance["replayContractHash"] == (
        "1cc435b6cda4ec812ea48e2a76276e8225194481512b8186f6cb9ac342be2650"
    )
    assert set(provenance["candidateEvidence"]) == {
        "generatorSourceHash",
        "generatedClientHash",
        "generatedContractTestsHash",
    }
    assert "pathParametersEncoded" in replay["expected_json"]
    assert [event["action"] for event in repository.list_audit_events()][-1] == (
        "version.activated"
    )
