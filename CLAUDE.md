# CLAUDE.md

## Project Identity

This is a personal but institution-style BTC/USDT futures trading system.

The system may eventually place real trades. Treat all execution, risk, leverage, position mode, exchange API, deployment, and key-management changes as high-risk.

## Architecture

### Python Research Plane

Python is responsible for:

- data research
- deterministic backtesting
- strategy experiments
- feature engineering
- ML training and evaluation
- report generation
- deployment candidate generation

Python must not directly place live orders.

### Java Trading Plane

Java is responsible for:

- OMS
- Risk Gateway
- Execution Service
- BingX Adapter
- position reconciliation
- kill switch
- paper/live trading runtime

All live orders must pass through the Java Risk Gateway.

## Non-negotiable Rules

- Never enable live trading without explicit human approval.
- Never hardcode API keys, secrets, passwords, tokens, or private keys.
- Never modify `.env` or real credential files.
- Never weaken risk limits without explicit human approval.
- Never increase leverage limits without explicit human approval.
- Never bypass Java Risk Gateway.
- Never allow Python to directly place live orders.
- Never add live exchange write-access in CI.
- Never commit raw trading logs containing secrets or account identifiers.
- Never run untrusted install scripts such as `curl | sh` or `wget | bash`.

## High-risk Files and Concepts

Treat the following as high-risk:

- live trading flag
- leverage settings
- margin mode
- position mode
- order router
- market order logic
- stop loss / take profit logic
- exchange adapter
- API key handling
- risk limits
- kill switch
- deployment manifest

## Required Verification

Before claiming completion, report:

1. files changed
2. tests added
3. tests run
4. test results
5. remaining risks
6. human approval needed

## Development Workflow

Default workflow:

1. Read docs and relevant code.
2. Produce a plan first.
3. Ask for approval before high-risk changes.
4. Make small changes.
5. Add or update tests.
6. Run verification commands.
7. Summarize risks and next steps.

## Branch and Merge Rules

- Never commit or push directly to `main`.
- All changes go through a branch and a Pull Request.
- Self-review and run verification before opening a PR.
- High-risk changes require a second reviewer.
- Merge only after required checks pass.
- Never bypass, weaken, or skip a failing `security-gates` check to force it green.

See `docs/claude/CODERABBIT_REVIEW_MODEL.md` for the full merge-gate model.

## MVP Priority

Do not start with live BingX trading.

Implementation priority:

1. docs and architecture decisions
2. shared schemas
3. Java OMS state machine
4. Java Risk Gateway skeleton
5. Python deterministic backtest skeleton
6. schema compatibility tests
7. paper broker
8. BingX adapter skeleton
9. paper trading
10. canary live preparation

## LLM Usage Policy

LLMs may be used for:

- coding assistance
- research support
- backtest interpretation
- log summarization
- risk review
- documentation

LLMs must not be used as the direct live trading decision maker in MVP.
