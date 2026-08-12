from pathlib import Path

import yaml


SKILLS_ROOT = Path("skills")


def _frontmatter(skill_file: Path) -> dict[str, str]:
    content = skill_file.read_text(encoding="utf-8")
    assert content.startswith("---\n"), skill_file
    _, raw, _ = content.split("---", 2)
    return yaml.safe_load(raw)


def test_project_skills_have_valid_metadata_and_no_placeholders() -> None:
    skill_dirs = sorted(path for path in SKILLS_ROOT.iterdir() if path.is_dir())
    assert skill_dirs

    for skill_dir in skill_dirs:
        skill_file = skill_dir / "SKILL.md"
        agent_file = skill_dir / "agents" / "openai.yaml"
        assert skill_file.is_file(), skill_dir
        assert agent_file.is_file(), skill_dir

        content = skill_file.read_text(encoding="utf-8")
        metadata = _frontmatter(skill_file)
        assert set(metadata) == {"name", "description"}, skill_file
        assert metadata["name"] == skill_dir.name
        assert metadata["description"].strip()
        assert "TODO" not in content
        assert len(content.splitlines()) <= 500

        agent = yaml.safe_load(agent_file.read_text(encoding="utf-8"))
        interface = agent["interface"]
        assert 25 <= len(interface["short_description"]) <= 64
        assert f"${skill_dir.name}" in interface["default_prompt"]


def test_project_skill_references_are_direct_and_nonempty() -> None:
    for skill_file in SKILLS_ROOT.glob("*/SKILL.md"):
        content = skill_file.read_text(encoding="utf-8")
        for reference in (skill_file.parent / "references").glob("*.md"):
            assert reference.stat().st_size > 0
            assert f"references/{reference.name}" in content
