# personal-trading-engine

Personal institution-style BTC/USDT futures trading system.

## Current Scope

This project is in the architecture and MVP foundation phase.

Initial target:

- Exchange: BingX
- Product: BTC/USDT USDT-M perpetual futures
- Direction: long and short
- Order types: limit and guarded market
- Runtime: VPS-oriented, local development first
- Architecture: Python Research Plane + Java Trading Plane Hybrid-lite

## Safety First

This repository must not contain real API keys, secrets, live credentials, or production account exports.

Live trading is disabled by default and requires explicit human approval.

## Architecture

```text
Python Research Plane
- data research
- backtest
- strategy experiments
- ML training
- reports
- deployment candidate manifest

Java Trading Plane
- OMS
- Risk Gateway
- Execution Service
- BingX Adapter
- Reconciliation
- Kill Switch
- Paper/Live runtime
```

## Documentation

Start with [`docs/00_INDEX.md`](docs/00_INDEX.md) — the documentation index and
source-of-truth registry. Review/merge governance lives in
[`docs/claude/CODERABBIT_REVIEW_MODEL.md`](docs/claude/CODERABBIT_REVIEW_MODEL.md).

## Implementation Checkpoint

Current `main` reflects Candidate 1 through Candidate 14. This is a status
summary only — the itemized, evidence-cited classification (what is a
deterministic production baseline vs. a narrow test-only proof vs. not yet
implemented) lives in
[`docs/04_MVP_SCOPE_AND_ROADMAP.md`](docs/04_MVP_SCOPE_AND_ROADMAP.md); this
section does not repeat that evidence.

First Milestones status:

1. Add docs and architecture decisions — done
2. Define shared schemas — done
3. Implement Java OMS state machine — done (pure-domain skeleton)
4. Implement Java Risk Gateway skeleton — done (pure-domain skeleton, no
   production risk-rule values yet)
5. Implement Python deterministic backtest skeleton — done
6. Add schema compatibility tests — done (JSON-boundary codec compatibility;
   does not prove full backtest↔Java runtime equivalence)
7. Implement paper broker — done (deterministic paper execution, a
   Risk→OMS→PaperBroker pipeline, a position-snapshot projection, and a daily
   report all exist as pure-domain or narrow-scope components; see docs/04
   for exact boundaries)
8. Add BingX adapter skeleton — not started
9. Run paper trading — not started (no long-running paper runtime exists)
10. Prepare canary live trading — not started

Live trading remains disabled and unimplemented: no exchange adapter, no
credential handling, and no live order path exist in this repository. No
paper-qualification run has occurred, and no canary/live-readiness claim is
made. See
[`docs/04_MVP_SCOPE_AND_ROADMAP.md`](docs/04_MVP_SCOPE_AND_ROADMAP.md) for the
full status matrix and
[`docs/10_OPEN_QUESTIONS_AND_RISKS.md`](docs/10_OPEN_QUESTIONS_AND_RISKS.md)
for unresolved decisions.
