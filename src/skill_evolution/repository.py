from __future__ import annotations

import json
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator


SCHEMA = """
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS episodes (
    id TEXT PRIMARY KEY,
    task TEXT NOT NULL,
    skill_name TEXT NOT NULL,
    skill_version TEXT NOT NULL,
    output_summary TEXT NOT NULL,
    status TEXT NOT NULL,
    opened_at TEXT NOT NULL,
    expires_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS integrations (
    id TEXT PRIMARY KEY,
    page_name TEXT NOT NULL,
    document_hash TEXT NOT NULL,
    operation_count INTEGER NOT NULL,
    result_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS candidates (
    id TEXT PRIMARY KEY,
    source_episode_id TEXT NOT NULL REFERENCES episodes(id),
    feedback TEXT NOT NULL,
    proposed_rule TEXT NOT NULL,
    decision TEXT NOT NULL,
    status TEXT NOT NULL,
    similarity REAL NOT NULL,
    confidence REAL NOT NULL,
    retrieved_rules_json TEXT NOT NULL,
    base_content_hash TEXT NOT NULL,
    staged_content TEXT,
    evaluation_id TEXT,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS replay_cases (
    id TEXT PRIMARY KEY,
    candidate_id TEXT NOT NULL REFERENCES candidates(id),
    source_episode_id TEXT NOT NULL REFERENCES episodes(id),
    input_json TEXT NOT NULL,
    expected_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS evaluations (
    id TEXT PRIMARY KEY,
    candidate_id TEXT NOT NULL REFERENCES candidates(id),
    replay_case_id TEXT NOT NULL REFERENCES replay_cases(id),
    judge TEXT NOT NULL,
    passed INTEGER NOT NULL,
    baseline_json TEXT NOT NULL,
    candidate_json TEXT NOT NULL,
    comparison_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS versions (
    id TEXT PRIMARY KEY,
    skill_name TEXT NOT NULL,
    version TEXT NOT NULL,
    candidate_id TEXT NOT NULL REFERENCES candidates(id),
    parent_version_id TEXT,
    before_content TEXT NOT NULL,
    after_content TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS skill_heads (
    skill_name TEXT PRIMARY KEY,
    version_id TEXT NOT NULL REFERENCES versions(id),
    content_hash TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS promotion_intents (
    id TEXT PRIMARY KEY,
    candidate_id TEXT NOT NULL REFERENCES candidates(id),
    skill_name TEXT NOT NULL,
    target_version TEXT NOT NULL,
    base_content_hash TEXT NOT NULL,
    before_content TEXT NOT NULL,
    after_content TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS audit_events (
    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
    id TEXT NOT NULL UNIQUE,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);
"""


class SQLiteRepository:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.connection() as connection:
            connection.executescript(SCHEMA)

    def connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.path)
        connection.row_factory = sqlite3.Row
        return connection

    @contextmanager
    def connection(self) -> Iterator[sqlite3.Connection]:
        connection = self.connect()
        try:
            with connection:
                yield connection
        finally:
            connection.close()

    def insert(self, table: str, record: dict[str, Any]) -> None:
        columns = ", ".join(record)
        placeholders = ", ".join("?" for _ in record)
        with self.connection() as connection:
            connection.execute(
                f"INSERT INTO {table} ({columns}) VALUES ({placeholders})",
                tuple(record.values()),
            )

    def get(self, table: str, record_id: str) -> dict[str, Any] | None:
        with self.connection() as connection:
            row = connection.execute(f"SELECT * FROM {table} WHERE id = ?", (record_id,)).fetchone()
        return dict(row) if row else None

    def update(self, table: str, record_id: str, values: dict[str, Any]) -> None:
        assignments = ", ".join(f"{column} = ?" for column in values)
        with self.connection() as connection:
            connection.execute(
                f"UPDATE {table} SET {assignments} WHERE id = ?",
                (*values.values(), record_id),
            )

    def list(self, table: str, *, limit: int = 100) -> list[dict[str, Any]]:
        order = "sequence" if table == "audit_events" else "rowid"
        with self.connection() as connection:
            rows = connection.execute(
                f"SELECT * FROM {table} ORDER BY {order} ASC LIMIT ?", (limit,)
            ).fetchall()
        return [dict(row) for row in rows]

    def add_audit_event(self, event: dict[str, Any]) -> None:
        payload = event.copy()
        payload["payload_json"] = json.dumps(payload.pop("payload", {}), ensure_ascii=False)
        self.insert("audit_events", payload)

    def list_audit_events(self, *, limit: int = 100) -> list[dict[str, Any]]:
        return self.list("audit_events", limit=limit)

    def count(self, table: str) -> int:
        with self.connection() as connection:
            return int(connection.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0])

    def get_skill_head(self, skill_name: str) -> dict[str, Any] | None:
        with self.connection() as connection:
            row = connection.execute(
                """
                SELECT heads.skill_name, heads.version_id, heads.content_hash,
                       versions.version, versions.after_content
                FROM skill_heads AS heads
                JOIN versions ON versions.id = heads.version_id
                WHERE heads.skill_name = ?
                """,
                (skill_name,),
            ).fetchone()
        return dict(row) if row else None

    def finalize_promotion(
        self,
        intent_id: str,
        version: dict[str, Any],
    ) -> None:
        with self.connection() as connection:
            columns = ", ".join(version)
            placeholders = ", ".join("?" for _ in version)
            connection.execute(
                f"INSERT INTO versions ({columns}) VALUES ({placeholders})",
                tuple(version.values()),
            )
            connection.execute(
                """
                INSERT INTO skill_heads (skill_name, version_id, content_hash, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(skill_name) DO UPDATE SET
                    version_id = excluded.version_id,
                    content_hash = excluded.content_hash,
                    updated_at = excluded.updated_at
                """,
                (
                    version["skill_name"],
                    version["id"],
                    version["content_hash"],
                    version["created_at"],
                ),
            )
            connection.execute(
                "UPDATE promotion_intents SET status = 'completed' WHERE id = ?",
                (intent_id,),
            )
