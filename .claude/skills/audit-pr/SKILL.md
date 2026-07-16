---
name: audit-pr
description: Run an independent, fresh-context merge-readiness audit (docs/claude/CODERABBIT_REVIEW_MODEL.md §9) against a given open PR number, and post the verdict directly to the PR as a comment.
argument-hint: "[PR number]"
disable-model-invocation: true
context: fork
agent: pr-auditor
---

# Audit PR

Audit PR number `$ARGUMENTS`. `$ARGUMENTS` must be a single open PR number;
if it is empty or ambiguous, ask which PR to audit rather than guessing one.

This is an independent audit, not a continuation of any prior conversation:

- Re-derive the PR's state entirely from GitHub yourself, following your own
  agent definition's procedure. Do not accept a PR summary or "it should be
  fine" claim from whoever invoked you as fact.
- Post your verdict to the PR itself via `gh pr comment`, then return the
  identical content as your response.
- Never merge, approve, request-changes, edit, or close the PR — this is a
  verdict-only, comment-only invocation.
- Use exactly one of `READY_TO_MERGE` / `REVIEW_PENDING` / `BLOCKED` — no
  other labels, no hedging language in the verdict line itself.
