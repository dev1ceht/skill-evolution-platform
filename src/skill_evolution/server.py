from __future__ import annotations

import json
import mimetypes
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from .http_api import ApiApplication


class ProjectServer:
    def __init__(self, application: ApiApplication, web_root: str | Path, examples_root: str | Path) -> None:
        self.application = application
        self.web_root = Path(web_root).resolve()
        self.examples_root = Path(examples_root).resolve()

    def handler_class(self) -> type[BaseHTTPRequestHandler]:
        project = self

        class RequestHandler(BaseHTTPRequestHandler):
            server_version = "SkillEvolution/0.1"

            def do_GET(self) -> None:  # noqa: N802
                path = urlparse(self.path).path
                if path.startswith("/api/"):
                    self._send_api("GET", path, None)
                    return
                if path.startswith("/examples/"):
                    self._send_file(project.examples_root, path.removeprefix("/examples/"))
                    return
                relative = "index.html" if path == "/" else path.lstrip("/")
                self._send_file(project.web_root, relative)

            def do_POST(self) -> None:  # noqa: N802
                path = urlparse(self.path).path
                length = int(self.headers.get("Content-Length", "0"))
                if length > 2_000_000:
                    self.send_error(413, "Request body too large")
                    return
                try:
                    body = json.loads(self.rfile.read(length) or b"{}")
                except json.JSONDecodeError:
                    self._send_json(400, {"error": "Request body must be valid JSON"})
                    return
                self._send_api("POST", path, body)

            def _send_api(self, method: str, path: str, body: dict[str, Any] | None) -> None:
                status, payload = project.application.dispatch(method, path, body)
                self._send_json(status, payload)

            def _send_json(self, status: int, payload: Any) -> None:
                encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(encoded)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(encoded)

            def _send_file(self, root: Path, relative: str) -> None:
                target = (root / relative).resolve()
                if root not in target.parents and target != root:
                    self.send_error(403)
                    return
                if not target.is_file():
                    self.send_error(404)
                    return
                content = target.read_bytes()
                mime, _ = mimetypes.guess_type(target.name)
                self.send_response(200)
                self.send_header("Content-Type", f"{mime or 'application/octet-stream'}; charset=utf-8")
                self.send_header("Content-Length", str(len(content)))
                self.end_headers()
                self.wfile.write(content)

            def log_message(self, format: str, *args: object) -> None:
                print(f"[http] {self.address_string()} {format % args}")

        return RequestHandler

    def serve(self, host: str, port: int) -> None:
        server = ThreadingHTTPServer((host, port), self.handler_class())
        print(f"Skill Evolution Platform running at http://{host}:{port}")
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            server.server_close()

