#!/usr/bin/env python3
"""Validate repository documentation without third-party dependencies."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
MARKDOWN_LINK = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
TRAILING_WHITESPACE = re.compile(r"[ \t]+$")
IGNORED_SCHEMES = ("http://", "https://", "mailto:", "tel:")
MAX_AGENT_INSTRUCTIONS_BYTES = 32 * 1024


def markdown_files() -> list[Path]:
    return sorted(
        path
        for path in ROOT.rglob("*.md")
        if ".git" not in path.relative_to(ROOT).parts
    )


def link_path(raw_target: str) -> str | None:
    target = raw_target.strip()
    if not target or target.startswith("#") or target.startswith(IGNORED_SCHEMES):
        return None

    if target.startswith("<") and ">" in target:
        target = target[1 : target.index(">")]
    else:
        target = target.split(maxsplit=1)[0]

    return unquote(target.split("#", maxsplit=1)[0]) or None


def validate_markdown(errors: list[str]) -> int:
    files = markdown_files()

    for path in files:
        relative = path.relative_to(ROOT)
        text = path.read_text(encoding="utf-8")

        for line_number, line in enumerate(text.splitlines(), start=1):
            if TRAILING_WHITESPACE.search(line):
                errors.append(f"{relative}:{line_number}: trailing whitespace")

            for match in MARKDOWN_LINK.finditer(line):
                target = link_path(match.group(1))
                if target is None:
                    continue

                resolved = (path.parent / target).resolve()
                try:
                    resolved.relative_to(ROOT)
                except ValueError:
                    errors.append(
                        f"{relative}:{line_number}: link escapes repository: {target}"
                    )
                    continue

                if not resolved.exists():
                    errors.append(
                        f"{relative}:{line_number}: missing relative link target: {target}"
                    )

    return len(files)


def validate_agent_instructions(errors: list[str]) -> None:
    agents = ROOT / "AGENTS.md"
    claude = ROOT / "CLAUDE.md"
    gitignore = ROOT / ".gitignore"

    if not agents.is_file():
        errors.append("AGENTS.md: required root instruction file is missing")
    elif agents.stat().st_size >= MAX_AGENT_INSTRUCTIONS_BYTES:
        errors.append(
            "AGENTS.md: must remain below the 32 KiB default combined instruction limit"
        )

    if not claude.is_file():
        errors.append("CLAUDE.md: required Claude Code adapter is missing")
    else:
        imports = [
            line.removeprefix("@").strip()
            for line in claude.read_text(encoding="utf-8").splitlines()
            if line.startswith("@")
        ]
        if "AGENTS.md" not in imports:
            errors.append("CLAUDE.md: must import @AGENTS.md")
        for imported in imports:
            if not (claude.parent / imported).resolve().is_file():
                errors.append(f"CLAUDE.md: imported file does not exist: {imported}")

    ignored = gitignore.read_text(encoding="utf-8").splitlines()
    if "CLAUDE.local.md" not in ignored:
        errors.append(".gitignore: CLAUDE.local.md must remain ignored")


def main() -> int:
    errors: list[str] = []
    checked_files = validate_markdown(errors)
    validate_agent_instructions(errors)

    if errors:
        print("Documentation validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Documentation validation passed ({checked_files} Markdown files checked).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
