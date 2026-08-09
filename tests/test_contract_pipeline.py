import copy
import json
from pathlib import Path

from skill_evolution.contracts import build_integration, diff_contracts, normalize_openapi


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
    assert "await client.listUsers(undefined)" in result["contractTests"]
    assert "expect(options.method).toBe('GET')" in result["contractTests"]
    assert "new URL(requestUrl).pathname" in result["contractTests"]


def test_contract_diff_detects_required_parameter_as_breaking() -> None:
    document = json.loads(Path("examples/user-api.openapi.json").read_text(encoding="utf-8"))
    changed = copy.deepcopy(document)
    changed["paths"]["/api/users"]["get"]["parameters"].append(
        {"name": "tenantId", "in": "query", "required": True, "schema": {"type": "string"}}
    )

    report = diff_contracts(normalize_openapi(document), normalize_openapi(changed))

    assert report["changed"][0]["path"] == "/api/users"
    assert report["breaking"][0]["type"] == "added-required-parameter:query:tenantId"
