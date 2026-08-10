from __future__ import annotations

import argparse
import json
import shutil
import tempfile
from pathlib import Path

from .benchmark_io import write_benchmark_report
from .http_api import ApiApplication
from .repository import SQLiteRepository
from .server import ProjectServer


def _project_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _application(root: Path, *, database: Path | None = None, skill_file: Path | None = None) -> ApiApplication:
    return ApiApplication(
        SQLiteRepository(database or root / "data" / "state.db"),
        skill_file or root / "skills" / "frontend-api-integration" / "SKILL.md",
    )


def run_demo(root: Path) -> dict[str, object]:
    with tempfile.TemporaryDirectory(prefix="skill-evolution-demo-") as directory:
        temp = Path(directory)
        skill = temp / "SKILL.md"
        shutil.copy2(root / "skills" / "frontend-api-integration" / "SKILL.md", skill)
        app = _application(root, database=temp / "state.db", skill_file=skill)
        document = json.loads((root / "examples" / "user-api.openapi.json").read_text(encoding="utf-8"))
        _, integration = app.dispatch(
            "POST", "/api/integrations", {"document": document, "pageName": "UserListPage"}
        )
        _, episode = app.dispatch(
            "POST",
            "/api/episodes",
            {
                "task": "Generate the user list page integration",
                "skillName": "frontend-api-integration",
                "skillVersion": "1.0.0",
                "outputSummary": "Generated page-number pagination handling",
            },
        )
        _, candidate = app.dispatch(
            "POST",
            f"/api/episodes/{episode['id']}/feedback",
            {"feedback": "分页其实是 cursor 模式，生成前应该根据 Schema 判断。"},
        )
        _, evaluation = app.dispatch("POST", f"/api/candidates/{candidate['id']}/evaluate", {})
        _, version = app.dispatch("POST", f"/api/candidates/{candidate['id']}/promote", {})
        return {
            "operations": len(integration["operations"]),
            "candidateDecision": candidate["decision"],
            "evaluationPassed": evaluation["passed"],
            "promotedVersion": version["version"],
        }


def main() -> None:
    parser = argparse.ArgumentParser(description="Skill Evolution Platform")
    subparsers = parser.add_subparsers(dest="command", required=True)
    serve = subparsers.add_parser("serve", help="Run the dashboard and JSON API")
    serve.add_argument("--host", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=8765)
    subparsers.add_parser("demo", help="Run an isolated end-to-end demonstration")
    benchmark = subparsers.add_parser("benchmark", help="Build an auditable efficiency report")
    benchmark.add_argument("--input", type=Path, required=True, help="Paired benchmark CSV or JSON")
    benchmark.add_argument("--project-root", type=Path, default=_project_root())
    benchmark.add_argument("--name", default="latest")
    args = parser.parse_args()
    root = _project_root()
    if args.command == "demo":
        print(json.dumps(run_demo(root), ensure_ascii=False, indent=2))
        return
    if args.command == "benchmark":
        try:
            result = write_benchmark_report(args.input, args.project_root, args.name)
        except (OSError, ValueError, json.JSONDecodeError) as error:
            parser.error(str(error))
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return
    application = _application(root)
    ProjectServer(application, root / "web", root / "examples").serve(args.host, args.port)


if __name__ == "__main__":
    main()
