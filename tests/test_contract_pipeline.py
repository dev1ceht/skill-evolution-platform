import copy
import json
from pathlib import Path

import pytest

from smart_canteen_contracts.contracts import build_integration, diff_contracts, normalize_openapi


def test_openapi_contract_produces_ir_tasks_and_typed_client() -> None:
    sample = Path("contracts/user-api.openapi.json").read_text(encoding="utf-8")

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
    assert 'expect(options.method).toBe("GET")' in result["contractTests"]
    assert "new URL(requestUrl).pathname" in result["contractTests"]


def test_contract_diff_detects_required_parameter_as_breaking() -> None:
    document = json.loads(Path("contracts/user-api.openapi.json").read_text(encoding="utf-8"))
    changed = copy.deepcopy(document)
    changed["paths"]["/api/users"]["get"]["parameters"].append(
        {"name": "tenantId", "in": "query", "required": True, "schema": {"type": "string"}}
    )

    report = diff_contracts(normalize_openapi(document), normalize_openapi(changed))

    assert report["changed"][0]["path"] == "/api/users"
    assert report["breaking"][0]["type"] == "added-required-parameter:query:tenantId"


def test_contract_diff_records_component_schema_widening_behind_a_response_ref() -> None:
    document = {
        "openapi": "3.0.3",
        "info": {"title": "Assistant API", "version": "1.0.0"},
        "paths": {
            "/api/v1/assistant": {
                "post": {
                    "operationId": "sendAssistantMessage",
                    "responses": {
                        "200": {
                            "description": "ok",
                            "content": {
                                "application/json": {
                                    "schema": {"$ref": "#/components/schemas/AssistantTurn"}
                                }
                            },
                        }
                    },
                }
            }
        },
        "components": {
            "schemas": {
                "AssistantTurn": {
                    "type": "object",
                    "properties": {"kind": {"type": "string", "enum": ["RESULT"]}},
                }
            }
        },
    }
    changed = copy.deepcopy(document)
    changed["components"]["schemas"]["AssistantTurn"]["properties"]["kind"]["enum"].append(
        "CONFIRMATION_REQUIRED"
    )

    report = diff_contracts(normalize_openapi(document), normalize_openapi(changed))

    assert report["changed"] == [
        {"method": "POST", "path": "/api/v1/assistant", "breakingReasons": []}
    ]
    assert report["breaking"] == []


def test_contract_diff_records_nested_component_schema_widening_for_history_refs() -> None:
    document = {
        "openapi": "3.0.3",
        "info": {"title": "Assistant API", "version": "1.0.0"},
        "paths": {
            "/api/v1/assistant/conversations/{conversationId}/messages": {
                "get": {
                    "operationId": "listAssistantMessages",
                    "responses": {
                        "200": {
                            "description": "ok",
                            "content": {
                                "application/json": {
                                    "schema": {
                                        "$ref": "#/components/schemas/AssistantConversationHistoryResponse"
                                    }
                                }
                            },
                        }
                    },
                }
            },
            "/api/v1/assistant/conversations/{conversationId}/messages/send": {
                "post": {
                    "operationId": "sendAssistantMessage",
                    "responses": {
                        "200": {
                            "description": "ok",
                            "content": {
                                "application/json": {
                                    "schema": {"$ref": "#/components/schemas/AssistantTurn"}
                                }
                            },
                        }
                    },
                }
            },
        },
        "components": {
            "schemas": {
                "AssistantConversationHistoryResponse": {
                    "type": "object",
                    "properties": {
                        "data": {"$ref": "#/components/schemas/AssistantConversationHistory"}
                    },
                },
                "AssistantConversationHistory": {
                    "type": "object",
                    "properties": {
                        "turns": {
                            "type": "array",
                            "items": {
                                "$ref": "#/components/schemas/AssistantConversationHistoryEntry"
                            },
                        }
                    },
                },
                "AssistantConversationHistoryEntry": {
                    "type": "object",
                    "properties": {
                        "response": {"$ref": "#/components/schemas/AssistantTurn"}
                    },
                },
                "AssistantTurn": {
                    "type": "object",
                    "properties": {"kind": {"type": "string", "enum": ["RESULT"]}},
                },
            }
        },
    }
    changed = copy.deepcopy(document)
    changed["components"]["schemas"]["AssistantTurn"]["properties"]["kind"]["enum"].append(
        "CONFIRMATION_REQUIRED"
    )

    report = diff_contracts(normalize_openapi(document), normalize_openapi(changed))

    assert report["changed"] == [
        {
            "method": "GET",
            "path": "/api/v1/assistant/conversations/{conversationId}/messages",
            "breakingReasons": [],
        },
        {
            "method": "POST",
            "path": "/api/v1/assistant/conversations/{conversationId}/messages/send",
            "breakingReasons": [],
        },
    ]
    assert report["breaking"] == []


def test_contract_diff_detects_nested_component_enum_narrowing_as_breaking() -> None:
    document = {
        "openapi": "3.0.3",
        "info": {"title": "Assistant API", "version": "1.0.0"},
        "paths": {
            "/api/v1/assistant/history": {
                "get": {
                    "operationId": "getAssistantHistory",
                    "responses": {
                        "200": {
                            "description": "ok",
                            "content": {
                                "application/json": {
                                    "schema": {
                                        "$ref": "#/components/schemas/AssistantConversationHistoryResponse"
                                    }
                                }
                            },
                        }
                    },
                }
            }
        },
        "components": {
            "schemas": {
                "AssistantConversationHistoryResponse": {
                    "type": "object",
                    "properties": {
                        "data": {"$ref": "#/components/schemas/AssistantConversationHistory"}
                    },
                },
                "AssistantConversationHistory": {
                    "type": "object",
                    "properties": {
                        "turns": {
                            "type": "array",
                            "items": {"$ref": "#/components/schemas/AssistantTurn"},
                        }
                    },
                },
                "AssistantTurn": {
                    "type": "object",
                    "properties": {
                        "kind": {
                            "type": "string",
                            "enum": ["RESULT", "CONFIRMATION_REQUIRED"],
                        }
                    },
                },
            }
        },
    }
    changed = copy.deepcopy(document)
    changed["components"]["schemas"]["AssistantTurn"]["properties"]["kind"]["enum"] = [
        "RESULT"
    ]

    report = diff_contracts(normalize_openapi(document), normalize_openapi(changed))

    assert report["changed"] == [
        {
            "method": "GET",
            "path": "/api/v1/assistant/history",
            "breakingReasons": ["response-schema-became-incompatible"],
        }
    ]
    assert report["breaking"] == [
        {
            "type": "response-schema-became-incompatible",
            "method": "GET",
            "path": "/api/v1/assistant/history",
        }
    ]


def test_generated_client_interpolates_and_encodes_path_parameters() -> None:
    document = {
        "openapi": "3.0.3",
        "info": {"title": "Smart Canteen API", "version": "1.0.0"},
        "paths": {
            "/api/v1/menus/{menuId}/submit": {
                "post": {
                    "operationId": "submitMenu",
                    "parameters": [
                        {
                            "name": "menuId",
                            "in": "path",
                            "required": True,
                            "schema": {"type": "string"},
                        }
                    ],
                    "responses": {"200": {"description": "submitted"}},
                }
            }
        },
    }

    result = build_integration(document, page_name="MenuApprovalPage")

    assert "encodeURIComponent(String(menuId))" in result["typescriptClient"]
    assert 'let path = "/api/v1/menus/{menuId}/submit"' in result["typescriptClient"]
    assert 'path.replace("{menuId}", encodedMenuId)' in result["typescriptClient"]
    assert (
        'expect(new URL(requestUrl).pathname).toBe("/api/v1/menus/fixture/submit")'
        in result["contractTests"]
    )


def test_normalization_resolves_reusable_parameter_components() -> None:
    source = Path("contracts/smart-canteen.openapi.yaml").read_text(
        encoding="utf-8"
    )

    ir = normalize_openapi(source)
    submit = next(item for item in ir["operations"] if item["operationId"] == "submitMenu")

    assert submit["parameters"][0] == {
        "name": "menuId",
        "in": "path",
        "required": True,
        "schema": {"type": "string"},
    }


def test_smart_canteen_alert_schemas_include_compliance_source() -> None:
    source = Path("contracts/smart-canteen.openapi.yaml").read_text(
        encoding="utf-8"
    )

    ir = normalize_openapi(source)
    schema_names = {
        "AlertReportRequest",
        "ExternalAlertReportRequest",
        "AlertDisposalRequest",
        "ExternalAlertDisposalRequest",
        "AlertRecord",
    }

    for schema_name in schema_names:
        source_values = ir["schemas"][schema_name]["properties"]["source"]["enum"]
        assert "COMPLIANCE" in source_values


def test_generated_client_maps_http_header_names_to_safe_identifiers() -> None:
    document = {
        "openapi": "3.0.3",
        "info": {"title": "Receipt API", "version": "1.0.0"},
        "paths": {
            "/api/v1/inventory/receipts": {
                "post": {
                    "operationId": "receiveInventory",
                    "parameters": [
                        {
                            "name": "Idempotency-Key",
                            "in": "header",
                            "required": True,
                            "schema": {"type": "string"},
                        }
                    ],
                    "responses": {"200": {"description": "received"}},
                }
            }
        },
    }

    result = build_integration(document, page_name="ReceiptPage")
    client = result["typescriptClient"]

    assert "receiveInventory(idempotencyKey: string)" in client
    assert 'headers["Idempotency-Key"] = String(idempotencyKey)' in client
    assert 'expect(options.headers["Idempotency-Key"]).toBe(\'fixture\')' in result[
        "contractTests"
    ]


def test_codegen_sanitizes_identifiers_and_escapes_wire_names() -> None:
    document = {
        "openapi": "3.0.3",
        "info": {"title": "Odd API", "version": "1.0.0"},
        "paths": {
            "/api/odd": {
                "get": {
                    "operationId": "get-user",
                    "parameters": [
                        {
                            "name": "X-Trace'Id",
                            "in": "header",
                            "required": True,
                            "schema": {"type": "string"},
                        }
                    ],
                    "responses": {
                        "200": {
                            "description": "ok",
                            "content": {
                                "application/json": {
                                    "schema": {
                                        "$ref": "#/components/schemas/error-response"
                                    }
                                }
                            },
                        }
                    },
                }
            }
        },
        "components": {
            "schemas": {
                "error-response": {
                    "type": "object",
                    "required": ["default"],
                    "properties": {
                        "default": {"type": "string"},
                        "display-name": {"type": "string"},
                    },
                }
            }
        },
    }

    client = build_integration(document, page_name="OddPage")["typescriptClient"]

    assert "export type ErrorResponse" in client
    assert '"default": string;' in client
    assert '"display-name"?: string;' in client
    assert "function getUser(xTraceId: string)" in client
    assert 'headers["X-Trace\'Id"] = String(xTraceId)' in client


def test_codegen_rejects_normalized_operation_identifier_collisions() -> None:
    document = {
        "openapi": "3.0.3",
        "info": {"title": "Collision API", "version": "1.0.0"},
        "paths": {
            "/api/one": {
                "get": {"operationId": "get-user", "responses": {"200": {}}}
            },
            "/api/two": {
                "get": {"operationId": "get_user", "responses": {"200": {}}}
            },
        },
    }

    with pytest.raises(ValueError, match="identifier collision"):
        build_integration(document, page_name="CollisionPage")


def test_codegen_avoids_typescript_keywords_and_generated_local_collisions() -> None:
    document = {
        "openapi": "3.0.3",
        "info": {"title": "Local Collision API", "version": "1.0.0"},
        "paths": {
            "/api/items/{foo}": {
                "post": {
                    "operationId": "enum",
                    "parameters": [
                        {"name": "foo", "in": "path", "required": True, "schema": {"type": "string"}},
                        {"name": "body", "in": "header", "required": True, "schema": {"type": "string"}},
                        {"name": "url", "in": "query", "schema": {"type": "string"}},
                        {"name": "headers", "in": "query", "schema": {"type": "string"}},
                        {"name": "response", "in": "query", "schema": {"type": "string"}},
                        {"name": "encodedFoo", "in": "query", "schema": {"type": "string"}},
                    ],
                    "requestBody": {
                        "required": True,
                        "content": {"application/json": {"schema": {"type": "object"}}},
                    },
                    "responses": {"200": {"description": "ok"}},
                }
            }
        },
    }

    client = build_integration(document, page_name="CollisionPage")["typescriptClient"]

    assert "function enumParameter(" in client
    assert "body2: Record<string, unknown>" in client
    assert "const encodedFoo2 = encodeURIComponent" in client
    assert "const url2 = new URL" in client
    assert "const headers2: Record<string, string>" in client
    assert "const response2 = await fetch" in client
    assert "body: JSON.stringify(body2)" in client
