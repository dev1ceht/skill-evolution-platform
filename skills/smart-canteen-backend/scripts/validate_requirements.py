from __future__ import annotations

import argparse
from pathlib import Path

import yaml


def validate(path: Path) -> list[str]:
    payload = yaml.safe_load(path.read_text(encoding="utf-8"))
    errors: list[str] = []
    if not isinstance(payload, dict):
        return ["根节点必须是 mapping"]
    if payload.get("schema_version") != "1.0":
        errors.append("schema_version 必须为 1.0")
    documents = payload.get("source_documents")
    document_ids = {
        item.get("id") for item in documents or [] if isinstance(item, dict)
    }
    if not documents or any(not isinstance(item, dict) for item in documents):
        errors.append("source_documents 不能为空且每项必须是 mapping")
    requirements = payload.get("requirements")
    if not requirements or any(not isinstance(item, dict) for item in requirements):
        errors.append("requirements 不能为空且每项必须是 mapping")
        return errors
    seen: set[str] = set()
    for index, requirement in enumerate(requirements):
        prefix = f"requirements[{index}]"
        requirement_id = requirement.get("id")
        if not isinstance(requirement_id, str) or not requirement_id.strip():
            errors.append(f"{prefix}.id 必须是非空字符串")
        elif requirement_id in seen:
            errors.append(f"{prefix}.id 重复：{requirement_id}")
        else:
            seen.add(requirement_id)
        source = requirement.get("source") or {}
        if source.get("document") not in document_ids:
            errors.append(f"{prefix}.source.document 未引用已声明文档")
        if not isinstance(source.get("section"), str) or not source["section"].strip():
            errors.append(f"{prefix}.source.section 必须非空")
        if not isinstance(requirement.get("statement"), str) or not requirement["statement"].strip():
            errors.append(f"{prefix}.statement 必须非空")
        acceptance = requirement.get("acceptance")
        if not acceptance or any(not isinstance(item, str) or not item.strip() for item in acceptance):
            errors.append(f"{prefix}.acceptance 必须包含非空字符串")
        if requirement.get("status") not in {"approved", "proposed", "deprecated"}:
            errors.append(f"{prefix}.status 必须是 approved/proposed/deprecated")
    return errors


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate smart-canteen requirement catalog")
    parser.add_argument("path", type=Path)
    args = parser.parse_args()
    errors = validate(args.path)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        raise SystemExit(1)
    print(f"OK: {args.path} requirements are valid")


if __name__ == "__main__":
    main()
