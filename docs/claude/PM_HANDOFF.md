# PM Handoff

**Status:** supporting reference / current snapshot (living document, not source of truth)
**Last verified base main SHA:** `087b6067eeab6e795e998b6874fcc40cec425aeb`
**Repository visibility:** public
**Current phase:** product implementation start / Candidate 1 shared contract baseline

> 이 문서는 현재 상태를 요약하는 스냅샷이다. 제품·정책·아키텍처 결정은
> `docs/00_INDEX.md`가 지정한 source of truth와 `docs/11_DECISION_LOG.md`에서만 확정한다.

## 1. Project state

### Completed foundation

- D007 — Python + Java Hybrid-lite architecture
- PR #1 — governance bootstrap
- PR #2 — deterministic `security-gates`
- PR #3 — Claude operating model / PM handoff
- PR #4 — read-only reviewer subagents 5개
- PR #5 — reviewer skills 5개, deny-only project settings, PreToolUse policy guard

### Current candidate

Candidate 1 / Issue #6 — shared contract baseline.

현재 branch에 다음이 구현되어 있다:

- D011 — MVP v0.1 공유 계약은 JSON Schema Draft 2020-12 사용
- `schemas/v0.1/common.schema.json`
- `schemas/v0.1/order-intent.schema.json`
- `schemas/v0.1/risk-decision.schema.json`
- schema versioning convention
- valid / invalid fixtures
- deterministic Draft 2020-12 validation suite
- `docs/00_INDEX.md`와 open-question source 정합화

Candidate 1은 Java/Python runtime, OMS, Risk Gateway 구현을 포함하지 않는다.

## 2. Current code maturity

### 있음

- architecture/docs foundation
- governance/security foundation
- shared contract baseline v0.1

### 아직 없음

- Java OMS
- Java Risk Gateway
- Python deterministic backtest
- cross-language compatibility test
- paper broker
- exchange adapter
- paper runtime

## 3. Operating constraints

- Python Research Plane과 Java Trading Plane 책임을 분리한다.
- 모든 runtime 구현은 shared contract를 기준으로 한다.
- 한 PR은 Python only / Java only / schema only를 기본으로 한다.
- R2/R3 변경은 plan-first다.
- owner가 final merge action을 수행한다. Claude Code는 merge하지 않는다.
- 새 commit이 생기면 latest-head review completeness를 다시 확인한다.

## 4. Known residual risks / technical debt

Candidate 1~5를 막지 않는 backlog:

1. PreToolUse detector와 CI detector의 일부 parity gap
2. machine-readable canonical policy configuration 부재
3. project guard가 완전한 sandbox는 아님
4. reviewer auto-routing 부재
5. second AI reviewer 자동 enforcement 부재
6. GitHub server-side protection의 현재 상태는 다음 고위험 변경 전 재확인 필요

추가로:

- shared contract v0.1은 언어별 generated model을 제공하지 않는다.
- cross-language parser compatibility는 Candidate 5에서 검증한다.
- `RiskDecision.reasonCodes`의 closed enum은 Java Risk Gateway task까지 defer한다.
- exact exchange symbol, position mode, margin mode는 아직 확정하지 않는다.

## 5. Decision required

현재 미확정 항목의 source of truth는 `docs/10_OPEN_QUESTIONS_AND_RISKS.md`다.

1. BingX 정확한 API symbol
2. Position mode
3. Margin mode
4. 초기 주문 정책 세부
5. 손절/익절 방식
6. Java strategy runtime 범위
7. 백테스트 ↔ Java trading path 일치성 검증 방법
8. VPS 위치 / 네트워크 지연 기준
9. 알림 채널

D011은 해결됨: MVP v0.1 shared contract는 JSON Schema Draft 2020-12를 사용한다.

## 6. Next recommended sequence

1. Claude operating model / PM handoff — 완료, PR #3
2. Project reviewer subagents — 완료, PR #4
3. Project skills / project guardrails — 완료, PR #5
4. Shared contract baseline — Candidate 1, 현재 작업
5. Java OMS state-machine skeleton — 다음 작업
6. Java Risk Gateway skeleton
7. Python deterministic backtest skeleton
8. Schema compatibility baseline
9. Paper broker

## 7. Next task packet — Java OMS state-machine skeleton

**Goal:**

shared `OrderIntent` contract 이후의 Java OMS pure-domain state machine skeleton을 만든다.

**Risk class:** R3.

**In scope:**

- 최소 Java project skeleton
- `OrderState` lifecycle
- legal transition tests
- illegal transition tests
- partial-fill lifecycle boundary
- duplicate client-order-id / idempotency boundary skeleton

**Out of scope:**

- network calls
- exchange adapter
- credentials
- Python runtime changes
- Risk Gateway bypass
- exchange-specific retry implementation
- production persistence

**Acceptance criteria:**

1. 상태 전이가 명시적이며 임의 mutation 경로가 없다.
2. 다음 lifecycle이 테스트된다:
   - New → Accepted → PartiallyFilled → Filled
   - New → Accepted → Canceled
   - New → Rejected
   - Partial fill 후 cancel
3. illegal transition이 deterministic하게 거부된다.
4. duplicate/idempotency boundary가 표현되지만 외부 persistence는 도입하지 않는다.
5. shared schema나 Python code를 변경하지 않는다.
6. exchange/network path를 만들지 않는다.

**Required review:**

- `java-oms-reviewer`
- `architecture-reviewer`
- `test-reviewer`
- second AI reviewer policy
- CodeRabbit
- `security-gates`

**Stop conditions:**

- 미확정 product decision을 확정해야 하는 경우
- exchange/network scope가 필요한 경우
- current policy를 완화해야 하는 경우
- shared contract breaking change가 필요한 경우

## 8. Handoff update rule

업데이트 시점:

- 의미 있는 PR이 merge된 후
- major decision이 decision log에 기록된 후
- blocker 또는 phase가 바뀐 후

금지:

- source-of-truth 결정을 독자적으로 변경
- secret 또는 실제 account value 기록
- raw private operation data 기록
