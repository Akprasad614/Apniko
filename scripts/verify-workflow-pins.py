#!/usr/bin/env python3
"""Reject mutable third-party GitHub Action and reusable-workflow references."""

from __future__ import annotations

import re
import sys
from pathlib import Path


WORKFLOWS_DIRECTORY = Path(".github/workflows")
USES_PATTERN = re.compile(
    r"^\s*uses:\s*(?P<reference>[^\s#]+)(?P<comment>\s+#.*)?\s*$"
)
GITHUB_REFERENCE_PATTERN = re.compile(r"^[^/@\s]+/[^/@\s]+(?:/[^@\s]+)?@(?P<ref>.+)$")
FULL_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
VERSION_COMMENT_PATTERN = re.compile(r"\s+#\s*v\S+\s*$")


def is_third_party_github_reference(reference: str) -> bool:
    """Return true for owner/repository actions and reusable workflows.

    Local actions and Docker actions are outside this GitHub-ref pinning policy.
    """

    return bool(GITHUB_REFERENCE_PATTERN.fullmatch(reference))


def validate_workflow(path: Path) -> list[str]:
    failures: list[str] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        match = USES_PATTERN.match(line)
        if not match:
            continue

        reference = match.group("reference")
        if not is_third_party_github_reference(reference):
            continue

        ref = GITHUB_REFERENCE_PATTERN.fullmatch(reference).group("ref")
        if not FULL_SHA_PATTERN.fullmatch(ref):
            failures.append(
                f"{path}:{line_number}: third-party GitHub reference must use a full 40-character commit SHA: {reference}"
            )
        if not VERSION_COMMENT_PATTERN.search(line):
            failures.append(
                f"{path}:{line_number}: pinned third-party GitHub reference must retain its version comment for Dependabot"
            )
    return failures


def main() -> int:
    if not WORKFLOWS_DIRECTORY.is_dir():
        print(f"Workflow directory not found: {WORKFLOWS_DIRECTORY}", file=sys.stderr)
        return 1

    workflow_files = sorted(
        path
        for extension in ("*.yml", "*.yaml")
        for path in WORKFLOWS_DIRECTORY.rglob(extension)
    )
    failures = [failure for path in workflow_files for failure in validate_workflow(path)]
    if failures:
        print("Workflow pin verification failed:", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        return 1

    print(f"Workflow pin verification passed: {len(workflow_files)} workflow file(s) checked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
