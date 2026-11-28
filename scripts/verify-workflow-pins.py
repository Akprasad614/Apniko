#!/usr/bin/env python3
"""Reject mutable third-party GitHub Action and reusable-workflow references."""

from __future__ import annotations

import re
import sys
from pathlib import Path

WORKFLOWS_DIRECTORY = Path(".github/workflows")
USES_PATTERN = re.compile(r"^\s*uses:\s*(?P<reference>[^\s#]+)(?P<comment>\s+#.*)?\s*$")
GITHUB_REFERENCE_PATTERN = re.compile(r"^[^/@\s]+/[^/@\s]+(?:/[^@\s]+)?@(?P<ref>.+)$")
FULL_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
VERSION_COMMENT_PATTERN = re.compile(r"\s+#\s*v\S+\s*$")


def validate_workflow(path: Path) -> list[str]:
    failures: list[str] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        match = USES_PATTERN.match(line)
        if not match:
            continue

        reference = match.group("reference")
        github_reference = GITHUB_REFERENCE_PATTERN.fullmatch(reference)
        if not github_reference:
            continue

        ref = github_reference.group("ref")
        if not FULL_SHA_PATTERN.fullmatch(ref):
            failures.append(
                f"{path}:{line_number}: third-party action/reusable workflow must use a full 40-character SHA: {reference}"
            )
        if not VERSION_COMMENT_PATTERN.search(line):
            failures.append(
                f"{path}:{line_number}: retain a version comment for Dependabot: {reference}"
            )
    return failures


def main() -> int:
    if not WORKFLOWS_DIRECTORY.is_dir():
        print(f"Workflow directory not found: {WORKFLOWS_DIRECTORY}", file=sys.stderr)
        return 1

    files = sorted(
        path
        for pattern in ("*.yml", "*.yaml")
        for path in WORKFLOWS_DIRECTORY.rglob(pattern)
    )
    failures = [failure for path in files for failure in validate_workflow(path)]
    if failures:
        print("Workflow pin verification failed:", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        return 1

    print(f"Workflow pin verification passed: {len(files)} workflow file(s) checked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
