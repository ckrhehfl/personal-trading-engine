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

## First Milestones

1. Add docs and architecture decisions
2. Define shared schemas
3. Implement Java OMS state machine
4. Implement Java Risk Gateway skeleton
5. Implement Python deterministic backtest skeleton
6. Add schema compatibility tests
7. Implement paper broker
8. Add BingX adapter skeleton
9. Run paper trading
10. Prepare canary live trading
