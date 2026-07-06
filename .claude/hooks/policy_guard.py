#!/usr/bin/env python3
"""Deterministic PreToolUse policy guard for personal-trading-engine.

Registered in .claude/settings.json as a PreToolUse hook for the
Bash|Edit|Write matcher. This is a mechanical, non-AI safety layer that acts
even when the parent Claude Code session runs with
--dangerously-skip-permissions -- see docs/claude/CLAUDE_OPERATING_MODEL.md
§4 for why permission bypass is not itself a safety boundary.

This is a pattern matcher, not a full shell parser. It intentionally
duplicates some coverage already provided by scripts/ci/security_gates.py
(defense-in-depth across two independent layers: pre-execution here,
pre-merge there) and does not claim to catch every possible obfuscation of
a dangerous command.

evaluate_file_change() also understands the MultiEdit/NotebookEdit
tool_input shapes, but the PreToolUse hook is only *registered* for
Bash|Edit|Write (see .claude/settings.json) -- so today those two tool
names are only covered by the path-based permissions.deny rules (secret
path writes), not by this hook's content inspection (e.g. live-trading-flag
detection). See docs/claude/PM_HANDOFF.md known limitations.

Standard library only. No third-party dependencies.

CLI contract (stdin -> stdout/exit code):
  - blocked event: exit 0, stdout is a PreToolUse hookSpecificOutput JSON
    object with permissionDecision "deny".
  - safe event: exit 0, no stdout.
  - malformed input / internal error: fail closed -- exit 2, a short safe
    message on stderr (never the raw tool input), no stdout.
"""
from __future__ import annotations

import json
import re
import shlex
import sys
from dataclasses import dataclass


@dataclass(frozen=True)
class Finding:
    """A policy violation. Never carries raw matched command/content text."""

    code: str
    reason: str


# --------------------------------------------------------------------------
# 9A - forbidden secret/private file path (Edit/Write/MultiEdit/NotebookEdit)
# --------------------------------------------------------------------------

_FORBIDDEN_BASENAME_PREFIXES = ("id_rsa", "id_ed25519")
_FORBIDDEN_EXTENSIONS = (
    ".pem",
    ".key",
    ".p12",
    ".pfx",
    ".kdbx",
    ".jks",
    ".keystore",
    ".ppk",
    ".p8",
)
_FORBIDDEN_DIR_SEGMENTS = frozenset({"secrets", "credentials", "private"})
# Filename (not just directory-segment) markers -- catches a secret-looking
# file placed outside a secrets/credentials/private directory, e.g.
# "gcp-service-account-credentials.json" at repo root.
_FORBIDDEN_BASENAME_SUBSTRINGS = ("secret", "credential")


def _normalize(path: str) -> str:
    return path.replace("\\", "/")


def _basename(path: str) -> str:
    normalized = _normalize(path).rstrip("/")
    if not normalized:
        return ""
    return normalized.rsplit("/", 1)[-1]


def _segments(path: str) -> list[str]:
    return [seg for seg in _normalize(path).split("/") if seg]


def check_forbidden_secret_path(path: str) -> bool:
    if not path:
        return False
    basename = _basename(path)
    basename_lower = basename.lower()
    segments_lower = [seg.lower() for seg in _segments(path)]

    if basename_lower == ".env":
        return True
    if basename_lower.startswith(".env.") and basename_lower != ".env.example":
        return True
    if any(seg in _FORBIDDEN_DIR_SEGMENTS for seg in segments_lower[:-1]):
        return True
    if basename_lower.startswith(_FORBIDDEN_BASENAME_PREFIXES):
        return True
    if basename_lower.endswith(_FORBIDDEN_EXTENSIONS):
        return True
    if any(marker in basename_lower for marker in _FORBIDDEN_BASENAME_SUBSTRINGS):
        return True
    return False


# --------------------------------------------------------------------------
# 9B - live trading enablement (Edit new_string / Write content)
# --------------------------------------------------------------------------

_LIVE_TRADING_ENABLED_RE = re.compile(
    r"\bLIVE_TRADING_ENABLED\b\s*[:=]\s*[\"']?(true|True|TRUE)[\"']?\b"
)
_LIVE_TRADING_LOWER_RE = re.compile(
    r"\blive_trading\b\s*[:=]\s*[\"']?(true|True|TRUE)[\"']?\b"
)

# These paths intentionally contain the literal enablement strings as policy
# documentation, detector pattern data, or test fixtures -- not production
# enablement. Mirrors the same self-reference exclusion used by
# scripts/ci/security_gates.py's GATE_INFRA_PATHS.
_EXEMPT_LIVE_FLAG_SUFFIXES = (
    ".coderabbit.yaml",
    "scripts/ci/security_gates.py",
    "tests/ci/test_security_gates.py",
    ".claude/hooks/policy_guard.py",
    "tests/claude/test_policy_guard.py",
)


def _is_exempt_live_flag_path(path: str) -> bool:
    normalized = _normalize(path)
    if normalized.endswith(".md"):
        return True
    if "docs" in _segments(normalized):
        return True
    for suffix in _EXEMPT_LIVE_FLAG_SUFFIXES:
        if normalized == suffix or normalized.endswith("/" + suffix):
            return True
    return False


def check_live_trading_enablement(text: str, path: str) -> bool:
    if not text:
        return False
    if _is_exempt_live_flag_path(path):
        return False
    return bool(
        _LIVE_TRADING_ENABLED_RE.search(text) or _LIVE_TRADING_LOWER_RE.search(text)
    )


# --------------------------------------------------------------------------
# 9C - dangerous Bash patterns
# --------------------------------------------------------------------------

_PIPE_INSTALL_RE = re.compile(r"(curl|wget)\b[^\n|]*\|\s*(sh|bash)\b")

_BINGX_LIVE_ORDER_ENDPOINTS = (
    "/openApi/swap/v2/trade/order",
    "/openApi/swap/v2/trade/batchOrders",
)

_SHELL_OPERATORS = frozenset({"&&", "||", ";", "|"})

_FORCE_PUSH_FLAGS = frozenset({"--force", "-f", "--force-with-lease"})


def _tokenize(command: str) -> list[str]:
    try:
        return shlex.split(command, posix=True)
    except ValueError:
        # Unbalanced quotes etc. Fall back to whitespace splitting rather
        # than crashing -- this is a best-effort pattern matcher, not a
        # shell parser (see module docstring).
        return command.split()


def _logical_segments(command: str) -> list[list[str]]:
    tokens = _tokenize(command)
    segments: list[list[str]] = [[]]
    for tok in tokens:
        if tok in _SHELL_OPERATORS:
            segments.append([])
        else:
            segments[-1].append(tok)
    return [seg for seg in segments if seg]


def _check_git_push(tokens: list[str]) -> Finding | None:
    if len(tokens) < 2 or tokens[0] != "git" or tokens[1] != "push":
        return None
    args = tokens[2:]
    flags = [t for t in args if t.startswith("-")]
    positional = [t for t in args if not t.startswith("-")]
    if any(f in _FORCE_PUSH_FLAGS or f.startswith("--force-with-lease=") for f in flags):
        return Finding("FORCE_PUSH", "git push includes a force flag")
    if any(p == "main" or p.endswith(":main") for p in positional):
        return Finding("DIRECT_MAIN_PUSH", "git push targets the main branch directly")
    return None


def _check_gh_pr_merge(tokens: list[str]) -> Finding | None:
    if len(tokens) >= 3 and tokens[0] == "gh" and tokens[1] == "pr" and tokens[2] == "merge":
        return Finding("PR_MERGE_BLOCKED", "gh pr merge is not permitted")
    return None


def _check_gh_repo_edit(tokens: list[str]) -> Finding | None:
    if len(tokens) >= 3 and tokens[0] == "gh" and tokens[1] == "repo" and tokens[2] == "edit":
        if any(t.startswith("--visibility") for t in tokens[3:]):
            return Finding(
                "REPOSITORY_VISIBILITY_CHANGE", "gh repo edit changes repository visibility"
            )
    return None


def _extract_gh_api_method(tokens: list[str]) -> str | None:
    for i, tok in enumerate(tokens):
        if tok in ("-X", "--method"):
            if i + 1 < len(tokens):
                return tokens[i + 1].upper()
        elif tok.startswith("-X") and len(tok) > 2:
            return tok[2:].upper()
        elif tok.startswith("--method="):
            return tok.split("=", 1)[1].upper()
    return None


def _check_gh_api_branch_protection(tokens: list[str]) -> Finding | None:
    if len(tokens) < 2 or tokens[0] != "gh" or tokens[1] != "api":
        return None
    rest = tokens[2:]
    touches_branch_protection = any(
        "branches/" in t and "protection" in t for t in rest
    )
    if not touches_branch_protection:
        return None
    method = _extract_gh_api_method(rest)
    if method in ("PUT", "PATCH", "DELETE"):
        return Finding(
            "BRANCH_PROTECTION_MUTATION", "gh api call mutates branch protection"
        )
    return None


def _check_rm(tokens: list[str]) -> Finding | None:
    if not tokens or tokens[0] != "rm":
        return None
    args = tokens[1:]

    def _short_flag_has(tok: str, ch: str) -> bool:
        return tok.startswith("-") and not tok.startswith("--") and ch in tok[1:]

    recursive = any(
        t == "--recursive" or _short_flag_has(t, "r") or _short_flag_has(t, "R")
        for t in args
    )
    force = any(t == "--force" or _short_flag_has(t, "f") for t in args)
    if recursive and force:
        return Finding(
            "DESTRUCTIVE_RECURSIVE_REMOVE", "recursive forced remove is not permitted"
        )
    return None


def _check_docker_privileged(tokens: list[str]) -> Finding | None:
    if not tokens or tokens[0] != "docker":
        return None
    if any(t == "--privileged" or t.startswith("--privileged=") for t in tokens[1:]):
        return Finding("DOCKER_PRIVILEGED", "docker command requests privileged mode")
    return None


_SEGMENT_CHECKS = (
    _check_git_push,
    _check_gh_pr_merge,
    _check_gh_repo_edit,
    _check_gh_api_branch_protection,
    _check_rm,
    _check_docker_privileged,
)


def evaluate_bash(command: str) -> Finding | None:
    if not command:
        return None
    if _PIPE_INSTALL_RE.search(command):
        return Finding(
            "DANGEROUS_PIPE_INSTALL", "curl/wget output is piped directly into a shell"
        )
    for endpoint in _BINGX_LIVE_ORDER_ENDPOINTS:
        if endpoint in command:
            return Finding(
                "LIVE_EXCHANGE_WRITE", "command references a BingX live order endpoint"
            )
    # A Bash command can set the live-trading flag on disk (echo/sed/heredoc
    # writes) without ever going through the Edit/Write tool_input this hook
    # otherwise inspects. Checked unconditionally (no path exemption, since a
    # Bash command has no single associated file path) -- this may also
    # false-positive on a benign `grep` for the exact flag string, which is
    # an accepted tradeoff for this high-value check.
    if _LIVE_TRADING_ENABLED_RE.search(command) or _LIVE_TRADING_LOWER_RE.search(command):
        return Finding(
            "LIVE_TRADING_ENABLEMENT", "live trading flag appears to be set to true"
        )
    for tokens in _logical_segments(command):
        for check in _SEGMENT_CHECKS:
            finding = check(tokens)
            if finding is not None:
                return finding
    return None


# --------------------------------------------------------------------------
# file-change dispatch (Edit/Write/MultiEdit/NotebookEdit)
# --------------------------------------------------------------------------


def _extract_file_path(tool_name: str, tool_input: dict) -> str:
    key = "notebook_path" if tool_name == "NotebookEdit" else "file_path"
    path = tool_input.get(key, "")
    return path if isinstance(path, str) else ""


def _extract_change_text(tool_name: str, tool_input: dict) -> str:
    if tool_name == "Edit":
        text = tool_input.get("new_string", "")
        return text if isinstance(text, str) else ""
    if tool_name == "Write":
        text = tool_input.get("content", "")
        return text if isinstance(text, str) else ""
    if tool_name == "MultiEdit":
        edits = tool_input.get("edits", [])
        if not isinstance(edits, list):
            return ""
        parts = [
            edit.get("new_string", "")
            for edit in edits
            if isinstance(edit, dict) and isinstance(edit.get("new_string"), str)
        ]
        return "\n".join(parts)
    if tool_name == "NotebookEdit":
        text = tool_input.get("new_source", "")
        return text if isinstance(text, str) else ""
    return ""


def evaluate_file_change(tool_name: str, tool_input: dict) -> Finding | None:
    file_path = _extract_file_path(tool_name, tool_input)

    if check_forbidden_secret_path(file_path):
        return Finding(
            "FORBIDDEN_SECRET_PATH_WRITE",
            "write to a forbidden secret/credential path is blocked",
        )

    text = _extract_change_text(tool_name, tool_input)

    if check_live_trading_enablement(text, file_path):
        return Finding(
            "LIVE_TRADING_ENABLEMENT", "live trading flag appears to be set to true"
        )

    return None


# --------------------------------------------------------------------------
# event dispatcher
# --------------------------------------------------------------------------


def evaluate_event(payload: dict) -> Finding | None:
    tool_name = payload.get("tool_name")
    tool_input = payload.get("tool_input")
    if not isinstance(tool_input, dict):
        tool_input = {}

    if tool_name == "Bash":
        command = tool_input.get("command", "")
        if not isinstance(command, str):
            return None
        return evaluate_bash(command)

    if tool_name in ("Edit", "Write", "MultiEdit", "NotebookEdit"):
        return evaluate_file_change(tool_name, tool_input)

    return None


# --------------------------------------------------------------------------
# CLI entry point
# --------------------------------------------------------------------------


def main() -> int:
    try:
        raw = sys.stdin.read()
    except Exception:
        print("policy_guard: failed to read hook input", file=sys.stderr)
        return 2

    try:
        payload = json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        print("policy_guard: malformed hook input JSON", file=sys.stderr)
        return 2

    if not isinstance(payload, dict):
        print("policy_guard: malformed hook input JSON", file=sys.stderr)
        return 2

    try:
        finding = evaluate_event(payload)
    except Exception:
        print("policy_guard: internal error while evaluating policy", file=sys.stderr)
        return 2

    if finding is None:
        return 0

    output = {
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": f"{finding.code}: {finding.reason}",
        }
    }
    print(json.dumps(output))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
