#!/usr/bin/env python3
"""
Anchor - Release Notes Generator
Extracts release notes directly from CHANGELOG.md as the single source of truth.
"""

import argparse
import os
import re
import subprocess
import sys

def extract_changelog_section(changelog_path, tag=""):
    """Extract release notes from CHANGELOG.md for the given tag or latest entry."""
    if not os.path.exists(changelog_path):
        return None

    with open(changelog_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Split by level 2 headings: ## [...]
    sections = re.split(r'\n(?=##\s+\[)', content)

    if not sections:
        return None

    # First section before any heading is usually the title/intro
    version_sections = [s for s in sections if s.strip().startswith("## [")]

    if not version_sections:
        return None

    # Try matching specific tag or version if provided
    clean_tag = tag.lstrip("v").split("-")[0] if tag else ""
    if clean_tag:
        for sec in version_sections:
            header_line = sec.strip().split("\n", 1)[0]
            if clean_tag in header_line:
                return sec.strip()

    # Default to the latest (first) version section
    return version_sections[0].strip()

def get_git_commits(limit=5):
    """Retrieve recent git commit messages if git is available."""
    try:
        result = subprocess.run(
            ["git", "log", f"-n{limit}", "--pretty=format:* %s (%h)"],
            capture_output=True,
            text=True,
            check=False
        )
        if result.returncode == 0 and result.stdout.strip():
            return result.stdout.strip()
    except Exception:
        pass
    return None

def main():
    parser = argparse.ArgumentParser(description="Generate release notes for Anchor.")
    parser.add_argument("--version", default="1.0", help="App version string")
    parser.add_argument("--tag", default="", help="Release tag name")
    parser.add_argument("--sha", default="", help="Git commit SHA short")
    parser.add_argument("--changelog", default="CHANGELOG.md", help="Path to CHANGELOG.md")
    parser.add_argument("--output", default="RELEASE_NOTES.md", help="Output file path")
    args = parser.parse_args()

    notes = extract_changelog_section(args.changelog, args.tag)

    if not notes:
        notes = f"## Release {args.tag or args.version}\n\n- Performance improvements and audio timing optimizations."

    output_lines = [notes]

    recent_commits = get_git_commits(7)
    if recent_commits:
        output_lines.append("\n### Commits in this build\n")
        output_lines.append(recent_commits)
        output_lines.append("")

    full_text = "\n".join(output_lines)

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        f.write(full_text)

    print(f"Generated release notes at: {args.output}")

if __name__ == "__main__":
    main()
