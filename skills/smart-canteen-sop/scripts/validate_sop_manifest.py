"""Validate the smart-canteen SOP registry and optional run evidence."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

import yaml


ALLOWED_STATUSES = {"implemented", "port-only", "deferred", "environment-gated"}
REQUIRED_SOP_KEYS = {
    "id",
    "version",
    "trigger",
    "risk_level",
    "approval",
    "permissions",
    "scope",
    "preconditions",
    "idempotency",
    "timeout",
    "steps",
    "rollback",
    "requirements",
    "references",
    "implementation",
    "adapters",
    "evidence",
    "status",
}


def _load(path: Path) -> Any:
    with path.open(encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def _require_file(root: Path, relative: str, label: str, errors: list[str]) -> None:
    path = root / relative
    if not path.is_file():
        errors.append(f"{label} does not exist: {relative}")


def _require_list(value: Any, label: str, errors: list[str]) -> None:
    if not isinstance(value, list) or not value:
        errors.append(f"{label} must be a non-empty list")


def _require_requirement_ids(root: Path, required: list[Any], label: str, errors: list[str]) -> None:
    known: set[str] = set()
    for path in sorted((root / "docs" / "smart-canteen").glob("*requirements.yaml")):
        document = _load(path) or {}
        for requirement in document.get("requirements", []):
            if isinstance(requirement, dict) and requirement.get("id"):
                known.add(str(requirement["id"]))
    for requirement_id in required:
        if str(requirement_id) not in known:
            errors.append(f"{label} references unknown requirement: {requirement_id}")


def validate_manifest(root: Path, manifest_path: Path) -> tuple[int, int, list[str]]:
    document = _load(manifest_path) or {}
    errors: list[str] = []
    for key in ("schema_version", "registry", "registry_version", "skill", "sops", "compositions"):
        if key not in document:
            errors.append(f"manifest is missing {key}")

    sops = document.get("sops", [])
    compositions = document.get("compositions", [])
    if not isinstance(sops, list) or not sops:
        errors.append("manifest.sops must be a non-empty list")
        sops = []
    if not isinstance(compositions, list):
        errors.append("manifest.compositions must be a list")
        compositions = []

    sop_ids: set[str] = set()
    for index, sop in enumerate(sops):
        label = f"sops[{index}]"
        if not isinstance(sop, dict):
            errors.append(f"{label} must be a mapping")
            continue
        missing = REQUIRED_SOP_KEYS - set(sop)
        if missing:
            errors.append(f"{label} missing keys: {', '.join(sorted(missing))}")
        sop_id = str(sop.get("id", ""))
        if not sop_id:
            errors.append(f"{label}.id must be non-empty")
        elif sop_id in sop_ids:
            errors.append(f"duplicate SOP id: {sop_id}")
        sop_ids.add(sop_id)
        for key in ("trigger", "permissions", "preconditions", "steps", "requirements", "references", "evidence"):
            _require_list(sop.get(key), f"{label}.{key}", errors)
        status = sop.get("status")
        if status not in ALLOWED_STATUSES:
            errors.append(f"{label}.status is invalid: {status}")
        timeout = sop.get("timeout")
        if not isinstance(timeout, dict) or timeout.get("mode") not in {"request-bound", "not-configured"}:
            errors.append(f"{label}.timeout must declare request-bound or not-configured")
        elif not isinstance(timeout.get("enforced"), bool):
            errors.append(f"{label}.timeout.enforced must be boolean")
        _require_requirement_ids(root, sop.get("requirements", []), label, errors)
        for reference in sop.get("references", []):
            _require_file(root, str(reference), f"{label}.references", errors)
        implementation = sop.get("implementation")
        if not isinstance(implementation, dict):
            errors.append(f"{label}.implementation must be a mapping")
        else:
            for key in ("modules", "apis", "migrations", "tests", "verification"):
                values = implementation.get(key, [])
                _require_list(values, f"{label}.implementation.{key}", errors)
                for relative in values:
                    _require_file(root, str(relative), f"{label}.implementation.{key}", errors)
        for evidence in sop.get("evidence", []):
            _require_file(root, str(evidence), f"{label}.evidence", errors)
        for adapter in sop.get("adapters", []):
            if not isinstance(adapter, dict) or adapter.get("status") not in ALLOWED_STATUSES:
                errors.append(f"{label}.adapters contains an invalid adapter status")

    composition_ids: set[str] = set()
    for index, composition in enumerate(compositions):
        label = f"compositions[{index}]"
        if not isinstance(composition, dict):
            errors.append(f"{label} must be a mapping")
            continue
        composition_id = str(composition.get("id", ""))
        if not composition_id:
            errors.append(f"{label}.id must be non-empty")
        elif composition_id in composition_ids:
            errors.append(f"duplicate composition id: {composition_id}")
        composition_ids.add(composition_id)
        _require_list(composition.get("sops"), f"{label}.sops", errors)
        for sop_id in composition.get("sops", []):
            if str(sop_id) not in sop_ids:
                errors.append(f"{label} references unknown SOP: {sop_id}")
        evidence = composition.get("evidence", {})
        if not isinstance(evidence, dict):
            errors.append(f"{label}.evidence must be a mapping")
        else:
            _require_list(evidence.get("requirements"), f"{label}.evidence.requirements", errors)
            _require_list(evidence.get("verification"), f"{label}.evidence.verification", errors)
            _require_requirement_ids(root, evidence.get("requirements", []), label, errors)
            for relative in evidence.get("verification", []):
                _require_file(root, str(relative), f"{label}.evidence.verification", errors)

    return len(sops), len(compositions), errors


def validate_run(root: Path, run_path: Path, sop_ids: set[str], composition_ids: set[str]) -> list[str]:
    run = _load(run_path) or {}
    errors: list[str] = []
    for key in ("schema_version", "run_id", "skill", "skill_version", "scope", "actor_role", "status", "steps", "evidence"):
        if key not in run:
            errors.append(f"run is missing {key}")
    if run.get("composition_id") not in composition_ids and run.get("sop_id") not in sop_ids:
        errors.append("run must reference a known composition_id or sop_id")
    if not isinstance(run.get("scope"), dict) or not run.get("scope", {}).get("school_id") or not run.get("scope", {}).get("canteen_id"):
        errors.append("run.scope must include school_id and canteen_id")
    if not isinstance(run.get("steps"), list) or not run.get("steps"):
        errors.append("run.steps must be a non-empty list")
    else:
        for index, step in enumerate(run["steps"]):
            if not isinstance(step, dict) or step.get("sop_id") not in sop_ids:
                errors.append(f"run.steps[{index}] must reference a known SOP")
    evidence = run.get("evidence")
    if not isinstance(evidence, dict):
        errors.append("run.evidence must be a mapping")
    else:
        _require_list(evidence.get("requirements"), "run.evidence.requirements", errors)
        _require_list(evidence.get("tests"), "run.evidence.tests", errors)
        _require_list(evidence.get("verification"), "run.evidence.verification", errors)
        _require_requirement_ids(root, evidence.get("requirements", []), "run", errors)
        for key in ("tests", "verification", "traceability"):
            for relative in evidence.get(key, []):
                _require_file(root, str(relative), f"run.evidence.{key}", errors)
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[3],
        help="repository root (defaults to the project containing this Skill)",
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("docs/smart-canteen/sop-manifests.yaml"),
    )
    parser.add_argument("--run", type=Path, help="optional SOP run record to validate")
    args = parser.parse_args()
    root = args.root.resolve()
    manifest_path = args.manifest if args.manifest.is_absolute() else root / args.manifest
    sop_count, composition_count, errors = validate_manifest(root, manifest_path)
    document = _load(manifest_path) or {}
    sop_ids = {str(item.get("id")) for item in document.get("sops", []) if isinstance(item, dict)}
    composition_ids = {
        str(item.get("id")) for item in document.get("compositions", []) if isinstance(item, dict)
    }
    if args.run:
        run_path = args.run if args.run.is_absolute() else root / args.run
        errors.extend(validate_run(root, run_path, sop_ids, composition_ids))
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    run_suffix = f" and run {args.run}" if args.run else ""
    print(f"Validated {sop_count} SOPs and {composition_count} compositions{run_suffix}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
