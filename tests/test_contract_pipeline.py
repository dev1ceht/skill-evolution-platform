from pathlib import Path

from skill_evolution.contracts import build_integration


def test_openapi_contract_produces_ir_tasks_and_typed_client() -> None:
    sample = Path("examples/user-api.openapi.json").read_text(encoding="utf-8")

    result = build_integration(sample, page_name="UserListPage")

    assert [operation["operationId"] for operation in result["operations"]] == [
        "listUsers",
        "createUser",
    ]
    assert result["operations"][0]["provenance"]["pointer"] == "/paths/~1api~1users/get"
    assert result["tasks"][0]["page"] == "UserListPage"
    assert "export type User" in result["typescriptClient"]
    assert "export async function listUsers" in result["typescriptClient"]
    assert "cursor?: string" in result["typescriptClient"]

