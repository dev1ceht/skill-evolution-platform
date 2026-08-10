from __future__ import annotations

import json
import os
import shutil
import subprocess
import uuid
from pathlib import Path

import pytest


def test_runtime_verifier_records_docker_failure_evidence(tmp_path: Path) -> None:
    powershell = shutil.which("pwsh") or shutil.which("powershell")
    if powershell is None:
        pytest.skip("PowerShell is required to exercise the runtime verification boundary")

    env_file = tmp_path / ".env"
    env_file.write_text(
        "\n".join(
            [
                "MYSQL_DATABASE=smart_canteen",
                "MYSQL_USER=smart_canteen",
                "MYSQL_PASSWORD=test_mysql_password",
                "MYSQL_ROOT_PASSWORD=test_root_password",
                "REDIS_PASSWORD=test_redis_password",
                "RABBITMQ_USER=smart_canteen",
                "RABBITMQ_PASSWORD=test_rabbit_password",
                "RABBITMQ_VHOST=smart_canteen",
            ]
        ),
        encoding="utf-8",
    )
    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    if os.name == "nt":
        (fake_bin / "docker.cmd").write_text("@echo off\r\nexit /b 42\r\n", encoding="ascii")
    else:
        docker = fake_bin / "docker"
        docker.write_text("#!/bin/sh\nexit 42\n", encoding="ascii")
        docker.chmod(0o755)

    evidence_name = f"smart-canteen-runtime-failure-{uuid.uuid4().hex}.json"
    evidence_path = Path("outputs/verification") / evidence_name
    try:
        completed = subprocess.run(
            [
                powershell,
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                "examples/smart-canteen/infra/verify-stack.ps1",
                "-EnvFile",
                str(env_file),
                "-EvidenceName",
                evidence_name,
            ],
            check=False,
            capture_output=True,
            text=True,
            env={**os.environ, "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}"},
        )

        assert completed.returncode != 0
        evidence = json.loads(evidence_path.read_text(encoding="utf-8-sig"))
        assert evidence["result"] == "failed"
        assert evidence["failureStage"] == "compose-up"
        assert evidence["containers"] == []
        assert "password" not in json.dumps(evidence).lower()
    finally:
        evidence_path.unlink(missing_ok=True)
