#!/usr/bin/env python3
"""Deterministic security gates for personal-trading-engine.

Mechanical, non-AI merge-gate checks that run in GitHub Actions on every PR
targeting main. This is deliberately independent of the CodeRabbit AI review
layer configured in .coderabbit.yaml -- see
docs/claude/CODERABBIT_REVIEW_MODEL.md for how the two layers combine.

Standard library only. No third-party dependencies.

Exit codes:
  0 - all gates passed
  1 - one or more findings
  2 - internal error (e.g. a required git command failed)
"""
from __future__ import annotations

import argparse
import fnmatch
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

# Paths that implement or test these gates themselves. Gates B, C and E scan
# added source lines for forbidden *patterns* (e.g. the literal string
# "LIVE_TRADING_ENABLED=true" or a curl-pipe-to-shell snippet). This module
# and its test suite necessarily contain those same substrings as pattern
# data / synthetic test fixtures, not as production enablement. Scanning them
# would be equivalent to a linter failing on its own lint-rule definitions,
# so they are excluded from gates B, C and E. Gate A (forbidden tracked
# paths), Gate D (risk policy freeze) and Gate F (workflow hardening) do not
# have this self-reference problem and are not affected by this exclusion.
GATE_INFRA_PATHS = frozenset(
    {
        "scripts/ci/security_gates.py",
        "tests/ci/test_security_gates.py",
    }
)


class SecurityGateError(RuntimeError):
    """Raised when a required git command fails."""


@dataclass(frozen=True)
class Finding:
    gate: str
    path: str
    line: int
    message: str

    def format(self) -> str:
        return f"[{self.gate}] {self.path}:{self.line}: {self.message}"


# --------------------------------------------------------------------------
# git plumbing
# --------------------------------------------------------------------------


_REF_RE = re.compile(r"^[0-9a-fA-F]{7,40}$")


def validate_ref(value: str, label: str) -> str:
    """Reject anything that isn't a plausible commit SHA.

    base/head come from the GitHub Actions event payload and are passed
    straight into `git diff`/`git ls-tree` arguments. A value starting with
    `-` could otherwise be misread as a git option (argument injection).
    """
    if not _REF_RE.match(value):
        raise SecurityGateError(f"{label} does not look like a git commit SHA: {value!r}")
    return value


def run_git(args: list[str], cwd: str | None = None) -> str:
    try:
        result = subprocess.run(
            ["git", *args],
            check=True,
            capture_output=True,
            text=True,
            cwd=cwd,
        )
    except FileNotFoundError as exc:
        raise SecurityGateError("git executable not found") from exc
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or "").strip()
        raise SecurityGateError(f"git {' '.join(args)} failed: {stderr}") from exc
    return result.stdout


def list_tracked_files(ref: str, cwd: str | None = None) -> list[str]:
    output = run_git(["ls-tree", "-r", "--name-only", ref], cwd=cwd)
    return [line for line in output.splitlines() if line]


def read_file_at_ref(ref: str, path: str, cwd: str | None = None) -> str:
    return run_git(["show", f"{ref}:{path}"], cwd=cwd)


def changed_paths(base: str, head: str, cwd: str | None = None) -> list[str]:
    # --no-renames: a rename+edit must surface the original path as deleted
    # and the new path as added, so a rename can never silently bypass a
    # path-based gate (e.g. Gate D's docs/05_RISK_POLICY.md freeze).
    output = run_git(
        ["diff", "--no-renames", "--name-only", f"{base}...{head}"], cwd=cwd
    )
    return [line for line in output.splitlines() if line]


_HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@")


def diff_added_lines(
    base: str, head: str, cwd: str | None = None
) -> dict[str, list[tuple[int, str]]]:
    """Return {path: [(new_line_no, line_text), ...]} for lines added between base and head."""
    # --no-renames: see changed_paths() -- a rename must show as a full
    # delete+add so the new path's entire content is scanned as "added",
    # rather than hiding it behind a rename with only a small edit hunk.
    diff_text = run_git(
        ["diff", "--no-renames", "--unified=0", "--no-color", f"{base}...{head}"],
        cwd=cwd,
    )
    added: dict[str, list[tuple[int, str]]] = {}
    current_file: str | None = None
    next_line_no: int | None = None
    for line in diff_text.splitlines():
        if line.startswith("diff --git "):
            current_file = None
            next_line_no = None
        elif line.startswith("+++ "):
            target = line[4:]
            current_file = None if target == "/dev/null" else target[2:]
        elif line.startswith("--- "):
            continue
        elif line.startswith("@@"):
            match = _HUNK_RE.match(line)
            next_line_no = int(match.group(1)) if match else None
        elif line.startswith("+"):
            if current_file is not None and next_line_no is not None:
                added.setdefault(current_file, []).append((next_line_no, line[1:]))
                next_line_no += 1
        elif line.startswith("-"):
            continue
    return added


# --------------------------------------------------------------------------
# shared helpers
# --------------------------------------------------------------------------


def _is_docs_or_gate_infra(path: str) -> bool:
    if path.startswith("docs/") or path.endswith(".md"):
        return True
    if path == ".coderabbit.yaml":
        return True
    return path in GATE_INFRA_PATHS


# --------------------------------------------------------------------------
# GATE A - forbidden tracked paths
# --------------------------------------------------------------------------

_FORBIDDEN_BASENAMES = frozenset({"id_rsa", "id_ed25519"})
_FORBIDDEN_EXTENSIONS = (".pem", ".key", ".p12", ".pfx", ".kdbx")
_FORBIDDEN_DIR_NAMES = frozenset({"secrets", "credentials", "private"})
# Mirrors the "API/account/trade exports" section of .gitignore.
_EXPORT_GLOB_PATTERNS = (
    "*api-key*",
    "*secret*",
    "*account-export*",
    "*trade-export*",
    "*balance-export*",
)


def check_forbidden_tracked_paths(tracked_files: list[str]) -> list[Finding]:
    findings = []
    for path in tracked_files:
        basename = path.rsplit("/", 1)[-1]
        reason = None
        if basename == ".env":
            reason = "tracked .env file"
        elif basename.startswith(".env.") and basename != ".env.example":
            reason = "tracked .env.* file (only .env.example is allowed)"
        elif any(
            segment in _FORBIDDEN_DIR_NAMES for segment in path.split("/")[:-1]
        ):
            reason = "tracked path under a forbidden secrets/credentials directory"
        elif basename in _FORBIDDEN_BASENAMES:
            reason = "tracked SSH private key"
        elif basename.endswith(_FORBIDDEN_EXTENSIONS):
            reason = "tracked key/credential-store file extension"
        elif any(
            fnmatch.fnmatchcase(basename, pattern)
            for pattern in _EXPORT_GLOB_PATTERNS
        ):
            reason = "tracked path matches an account/trade/balance export pattern"

        if reason:
            findings.append(Finding("FORBIDDEN_TRACKED_PATH", path, 1, reason))
    return findings


# --------------------------------------------------------------------------
# GATE B - live trading enablement
# --------------------------------------------------------------------------

_LIVE_TRADING_ENABLED_RE = re.compile(
    r"\bLIVE_TRADING_ENABLED\b\s*[:=]\s*[\"']?(true|True|TRUE)[\"']?\b"
)
_LIVE_TRADING_LOWER_RE = re.compile(
    r"\blive_trading\b\s*[:=]\s*[\"']?(true|True|TRUE)[\"']?\b"
)


def check_live_trading_enablement(
    added: dict[str, list[tuple[int, str]]]
) -> list[Finding]:
    findings = []
    for path, lines in added.items():
        if _is_docs_or_gate_infra(path):
            continue
        for line_no, text in lines:
            if _LIVE_TRADING_ENABLED_RE.search(text) or _LIVE_TRADING_LOWER_RE.search(
                text
            ):
                findings.append(
                    Finding(
                        "LIVE_TRADING_ENABLEMENT",
                        path,
                        line_no,
                        "live trading flag appears to be set to true",
                    )
                )
    return findings


# --------------------------------------------------------------------------
# GATE C - python direct live-order path
# --------------------------------------------------------------------------

# Known BingX USDT-M swap live order endpoints. This is intentionally narrow:
# it catches an unambiguous direct-call violation, not every possible future
# SDK usage. See "Known limitations" in the PR description / docs.
_BINGX_LIVE_ORDER_ENDPOINTS = (
    "/openApi/swap/v2/trade/order",
    "/openApi/swap/v2/trade/batchOrders",
)


def check_python_direct_live_order_path(
    added: dict[str, list[tuple[int, str]]]
) -> list[Finding]:
    findings = []
    for path, lines in added.items():
        if not path.startswith("python/"):
            continue
        for line_no, text in lines:
            for endpoint in _BINGX_LIVE_ORDER_ENDPOINTS:
                if endpoint in text:
                    findings.append(
                        Finding(
                            "PYTHON_DIRECT_LIVE_ORDER_PATH",
                            path,
                            line_no,
                            f"python/** references a BingX live order endpoint ({endpoint})",
                        )
                    )
    return findings


# --------------------------------------------------------------------------
# GATE D - risk policy freeze (temporary, until a machine-readable risk
# config exists to replace this with a numeric semantic-comparison gate)
# --------------------------------------------------------------------------

_RISK_POLICY_PATH = "docs/05_RISK_POLICY.md"


def check_risk_policy_freeze(changed: list[str]) -> list[Finding]:
    if _RISK_POLICY_PATH in changed:
        return [
            Finding(
                "RISK_POLICY_CHANGE_BLOCKED",
                _RISK_POLICY_PATH,
                1,
                "docs/05_RISK_POLICY.md is frozen pending a machine-readable risk "
                "config; changes require a separate policy PR with explicit human "
                "approval",
            )
        ]
    return []


# --------------------------------------------------------------------------
# GATE E - dangerous shell install patterns
# --------------------------------------------------------------------------

_PIPE_INSTALL_RE = re.compile(r"(curl|wget)\b[^\n|]*\|\s*(sh|bash)\b")


def check_dangerous_pipe_installs(
    added: dict[str, list[tuple[int, str]]]
) -> list[Finding]:
    findings = []
    for path, lines in added.items():
        if _is_docs_or_gate_infra(path):
            continue
        for line_no, text in lines:
            if _PIPE_INSTALL_RE.search(text):
                findings.append(
                    Finding(
                        "DANGEROUS_PIPE_INSTALL",
                        path,
                        line_no,
                        "curl/wget output is piped directly into a shell",
                    )
                )
    return findings


# --------------------------------------------------------------------------
# GATE F - GitHub Actions hardening
# --------------------------------------------------------------------------

_WRITE_PERMISSION_KEYS = (
    "contents",
    "actions",
    "checks",
    "deployments",
    "id-token",
    "issues",
    "packages",
    "pages",
    "pull-requests",
    "security-events",
    "statuses",
)
_WRITE_PERMISSION_RE = re.compile(
    r"^\s*(" + "|".join(_WRITE_PERMISSION_KEYS) + r")\s*:\s*write\s*$"
)
_WRITE_ALL_RE = re.compile(r"^\s*permissions\s*:\s*write-all\s*$")
_SECRETS_REF_RE = re.compile(r"\$\{\{\s*secrets\.")
_UNSAFE_TRIGGER_RE = re.compile(r"\b(pull_request_target|workflow_run)\b")
_USES_LINE_RE = re.compile(r"^\s*-?\s*uses:\s*(\S+)\s*(?:#.*)?$")
_PINNED_SHA_RE = re.compile(r"^[^@]+@[0-9a-fA-F]{40}$")


def check_workflow_hardening(workflow_files: dict[str, str]) -> list[Finding]:
    findings = []
    for path, content in workflow_files.items():
        for line_no, raw in enumerate(content.splitlines(), start=1):
            stripped = raw.strip()
            if stripped.startswith("#"):
                continue

            if _UNSAFE_TRIGGER_RE.search(raw):
                findings.append(
                    Finding(
                        "UNSAFE_WORKFLOW_TRIGGER",
                        path,
                        line_no,
                        "pull_request_target/workflow_run trigger is not allowed",
                    )
                )
            if _WRITE_ALL_RE.match(raw) or _WRITE_PERMISSION_RE.match(raw):
                findings.append(
                    Finding(
                        "WORKFLOW_WRITE_PERMISSION",
                        path,
                        line_no,
                        "workflow grants a write permission",
                    )
                )
            if _SECRETS_REF_RE.search(raw):
                findings.append(
                    Finding(
                        "WORKFLOW_SECRET_REFERENCE",
                        path,
                        line_no,
                        "workflow references ${{ secrets.* }}",
                    )
                )

            uses_match = _USES_LINE_RE.match(raw)
            if uses_match:
                target = uses_match.group(1)
                if target.startswith("./"):
                    continue
                if not _PINNED_SHA_RE.match(target):
                    findings.append(
                        Finding(
                            "UNPINNED_ACTION",
                            path,
                            line_no,
                            "external action is not pinned to a full 40-character "
                            "commit SHA",
                        )
                    )
    return findings


# --------------------------------------------------------------------------
# orchestration
# --------------------------------------------------------------------------


def get_workflow_files(head: str, cwd: str | None = None) -> dict[str, str]:
    tracked = list_tracked_files(head, cwd=cwd)
    workflow_paths = [
        path
        for path in tracked
        if path.startswith(".github/workflows/")
        and (path.endswith(".yml") or path.endswith(".yaml"))
    ]
    return {path: read_file_at_ref(head, path, cwd=cwd) for path in workflow_paths}


def run_all_gates(base: str, head: str, cwd: str | None = None) -> list[Finding]:
    validate_ref(base, "base")
    validate_ref(head, "head")
    tracked = list_tracked_files(head, cwd=cwd)
    added = diff_added_lines(base, head, cwd=cwd)
    changed = changed_paths(base, head, cwd=cwd)
    workflow_files = get_workflow_files(head, cwd=cwd)

    findings: list[Finding] = []
    findings.extend(check_forbidden_tracked_paths(tracked))
    findings.extend(check_live_trading_enablement(added))
    findings.extend(check_python_direct_live_order_path(added))
    findings.extend(check_risk_policy_freeze(changed))
    findings.extend(check_dangerous_pipe_installs(added))
    findings.extend(check_workflow_hardening(workflow_files))
    findings.sort(key=lambda finding: (finding.gate, finding.path, finding.line))
    return findings


def main(argv: list[str] | None = None, cwd: str | None = None) -> int:
    """CLI entry point.

    `cwd` overrides the repo root the gates run against; it exists so tests
    can point this at a throwaway fixture repo. Production use (the
    `__main__` block below) never passes it, so behavior is unchanged: the
    repo root is derived from this script's own on-disk location.
    """
    parser = argparse.ArgumentParser(description="Deterministic security gates")
    parser.add_argument("--base", required=True, help="base git commit SHA")
    parser.add_argument("--head", required=True, help="head git commit SHA")
    args = parser.parse_args(argv)

    repo_root = cwd if cwd is not None else str(Path(__file__).resolve().parents[2])

    try:
        findings = run_all_gates(args.base, args.head, cwd=repo_root)
    except SecurityGateError as exc:
        print(f"INTERNAL_ERROR: {exc}", file=sys.stderr)
        return 2

    if not findings:
        print("security gates: PASS (0 findings)")
        return 0

    print(f"security gates: FAIL ({len(findings)} finding(s))")
    for finding in findings:
        print(finding.format())
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
