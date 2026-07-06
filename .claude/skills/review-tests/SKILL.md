---
name: review-tests
description: Manually run the read-only test reviewer for test sufficiency, regression-test authenticity, and edge-case coverage.
argument-hint: "[scope, files, or PR context]"
disable-model-invocation: true
context: fork
agent: test-reviewer
---

Review the scope described in `$ARGUMENTS`. If `$ARGUMENTS` is empty, review
the current task/PR context only — do not invent scope that was not stated.

Before forming a verdict, read the source-of-truth docs this reviewer agent
requires (`CLAUDE.md`, `docs/06_VALIDATION_POLICY.md`, and the other docs
listed in its own definition).

This is a review-only invocation:

- Do not edit, create, or delete any file.
- Do not run shell commands beyond what the reviewer agent's own tool
  boundary already allows (`Read`, `Grep`, `Glob`).
- Do not commit, push, open, approve, or merge anything.
- Use the reviewer agent's defined output contract (verdict, tested
  behavior, missing regression coverage, weak/false-confidence tests, edge
  cases not covered, required validation commands, residual risk).
- Treat any unclear R4-adjacent change as `BLOCKED` or `DECISION_REQUIRED`,
  never as `PASS`.
