import json
from pathlib import Path

from skill_evolution.http_api import ApiApplication
from skill_evolution.repository import SQLiteRepository


def test_http_api_exposes_integration_and_evolution_workflows(tmp_path: Path) -> None:
    skill_file = tmp_path / "skills" / "frontend-api-integration" / "SKILL.md"
    skill_file.parent.mkdir(parents=True)
    skill_file.write_text(
        "---\nname: frontend-api-integration\ndescription: Integrate APIs.\n---\n\n# Workflow\n\n## Learned rules\n",
        encoding="utf-8",
    )
    app = ApiApplication(SQLiteRepository(tmp_path / "state.db"), skill_file)
    contract = json.loads(Path("examples/user-api.openapi.json").read_text(encoding="utf-8"))

    status, integration = app.dispatch(
        "POST", "/api/integrations", {"document": contract, "pageName": "UserListPage"}
    )
    assert status == 201
    assert len(integration["operations"]) == 2

    status, episode = app.dispatch(
        "POST",
        "/api/episodes",
        {
            "task": "Integrate users",
            "skillName": "frontend-api-integration",
            "skillVersion": "1.0.0",
            "outputSummary": "Generated client",
        },
    )
    assert status == 201

    status, candidate = app.dispatch(
        "POST",
        f"/api/episodes/{episode['id']}/feedback",
        {"feedback": "错误码映射应该优先读取 OpenAPI response schema。"},
    )
    assert status == 201
    assert candidate["decision"] in {"add", "merge"}

    status, dashboard = app.dispatch("GET", "/api/dashboard", None)
    assert status == 200
    assert dashboard["metrics"]["episodes"] == 1
    assert dashboard["metrics"]["candidates"] == 1
    assert dashboard["metrics"]["integrations"] == 1
