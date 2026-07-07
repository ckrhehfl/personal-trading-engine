---
name: review-architecture
description: Manually run the read-only architecture reviewer for architecture, ADR, system-boundary, shared-contract, or cross-plane (Python Research Plane / Java Trading Plane) changes.
argument-hint: "[scope, files, or PR context]"
disable-model-invocation: true
context: fork
agent: architecture-reviewer
---

Review the scope described in `$ARGUMENTS`. If `$ARGUMENTS` is empty, review
the current task/PR context only — do not invent scope that was not stated.

Before forming a verdict, read the source-of-truth docs this reviewer agent
requires (`CLAUDE.md`, `docs/00_INDEX.md`, and the other docs listed in its
own definition).

This is a review-only invocation:

- Do not edit, create, or delete any file.
- Do not run shell commands beyond what the reviewer agent's own tool
  boundary already allows (`Read`, `Grep`, `Glob`).
- Do not commit, push, open, approve, or merge anything.
- Use the reviewer agent's defined output contract (verdict, findings by
  severity, source-of-truth conflicts, open decisions touched, required
  validation, remaining uncertainty).
- Treat any unclear R4-adjacent change as `BLOCKED` or `DECISION_REQUIRED`,
  never as `PASS`.
