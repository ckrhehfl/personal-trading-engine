# PM Handoff

**Status:** supporting reference / current snapshot (living document, not source of truth)
**Last verified base main SHA:** `bc48d171e8e3aa6f3d3601ba485fbe41345fdd1c`
**Repository visibility:** public
**Current phase:** Candidate 3 — Java Risk Gateway skeleton

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
- Candidate 1 / Issue #7 — shared contract baseline. **PR #6 merged**
  (merge commit `a03b221a21124b4d58dce8b9ed03618d653fae30`, 2026-07-07). D011,
  `schemas/v0.1/*.schema.json`, schema versioning convention, deterministic
  Draft 2020-12 validation suite (CI-enforced in `security-gates`), and
  `docs/00_INDEX.md`/open-question alignment are all in `main`.
- Candidate 2 / Issue #8 — Java OMS state-machine skeleton. **PR #9 merged**
  (merge commit `bc48d171e8e3aa6f3d3601ba485fbe41345fdd1c`, 2026-07-07T12:45:16Z).
  `java/` Java 21 + Gradle 9.6.1 project, `com.ptengine.oms` pure-domain
  order state machine (`OrderState`, `Order`, `OrderTransitions`,
  `IllegalOrderTransitionException`), in-memory `OrderRegistry` +
  `DuplicateClientOrderIdException`, 20 JUnit tests, Java tests wired into
  the existing `security-gates` required check. All in `main`.

### Current candidate

Candidate 3 / Issue #10 — Java Risk Gateway skeleton. 구현은 PR #11.

**상태:** PR #11 open, pending merge. `main`에는 아직 merge되지 않았다.
Candidate 4는 시작하지 않았다.

현재 branch(`worktree-java-risk-gateway-skeleton`, base `bc48d17`)에 다음이
구현되어 있다:

- `com.ptengine.contract.OrderIntent` (+ `IntentType`, `Direction`,
  `OrderType`, `InvalidOrderIntentException`) — `schemas/v0.1/order-intent.schema.json`과
  field-for-field aligned된 typed Java 도메인 모델. JSON parser 아님, 생성된
  model 아님, cross-language compatibility 주장 없음(그건 Candidate 5).
- `com.ptengine.risk.RiskDecision` (+ `RiskOutcome`,
  `InvalidRiskDecisionException`) — `schemas/v0.1/risk-decision.schema.json`과
  field-for-field aligned. PASS/BLOCK invariant 강제, `reasonCodes` 중복
  제거 + 불변.
- `com.ptengine.risk.RiskRejectReason` — 최초 closed Java enum
  (`RISK_CONFIGURATION_MISSING`, `RISK_ENGINE_ERROR`, `RISK_STATE_DEGRADED`).
  schema는 이 closed enum을 Java Risk Gateway task까지 명시적으로 defer해
  두었고, 이 작업이 그 task다. `RISK_STATE_DEGRADED`는 이 skeleton의 어떤
  rule도 아직 발생시키지 않는 예약값 — Javadoc에 명시.
- `com.ptengine.risk.RiskRule` — 순수 `OrderIntent -> Optional<RiskRejectReason>`.
  이 candidate에는 실제 production numeric risk rule 없음, 테스트는 fake
  rule만 사용.
- `com.ptengine.risk.RiskGateway` (+ `RiskDecisionMetadata`) — fail-closed
  집계기. rule 0개, rule exception, rule의 `null` 반환 모두 BLOCK으로
  귀결되고 어떤 error 경로도 PASS를 반환하지 않는다. decision id/timestamp는
  호출자가 넘기는 `RiskDecisionMetadata`로만 결정되며 숨은 clock/random
  상태 없음.
- JUnit 테스트 49개 신규(기존 OMS 20개는 무변경, `git diff` 확인) — 전체
  69개 전부 pass. 그중 두 개(`ruleExceptionBlocksWithEngineError`,
  `noErrorPathEverReturnsPass`)는 `RiskGateway`의 예외 처리를
  swallow-and-continue로 일부러 바꿔 fail-open 버그를 재현한 뒤 실제로
  fail하는 것을 확인하고 원복함 — 진짜 regression guard임을 검증함.
- `risk-reviewer` — **PASS**. R4 escalation 없음(수치 leverage/notional/
  exposure/loss-limit 값 없음, kill switch 없음, live/exchange/network 없음).
  `RiskGateway.evaluate()`의 모든 fail-closed 분기를 직접 추적해 확인.
- `architecture-reviewer` — **PASS**. schema field-for-field alignment 확인,
  Python/Java plane 경계 보존, `docs/10_OPEN_QUESTIONS_AND_RISKS.md` 미확정
  항목 어느 것도 암묵적으로 확정하지 않음, `com.ptengine.oms` 재설계 없음.
- `test-reviewer` — 최초 **CHANGES_NEEDED**(`RiskDecisionMetadata` 자체
  invariant 테스트 없음, BLOCK 경로 metadata passthrough 미검증, 다회 호출
  determinism/short-circuit 순서/null-argument guard 커버리지 없음)를
  지적했고, 4개 신규 테스트 파일에 해당 케이스를 추가함. 1차 재검증에서
  short-circuit 커버리지가 rule 예외(throw) 분기만 증명하고 rule의 `null`
  반환 분기는 증명하지 않는다는 잔여 gap을 추가로 지적함
  (`ruleAfterAFailingRuleIsNotEvaluated`는 있었지만 그 null-반환 대응 테스트가
  없었음) — `ruleAfterANullReturningRuleIsNotEvaluated` 테스트를 추가해
  해당 분기도 실제 side-effect로 증명한 뒤 **재실행하여 최종 PASS를 실제로
  확인함**. 전체 스위트 69/69 pass.

Candidate 3는 CI 변경 없음(기존 `./gradlew test` / `security-gates` 경로가
새 source set을 그대로 포함). Risk Gateway core는 존재하지만, 아직 실제
runtime 주문 경로가 없으므로 "모든 runtime 주문 경로가 물리적으로 우회
불가능하다"는 주장은 하지 않는다 — 향후 통합 작업의 몫이다.

## 2. Current code maturity

### 있음

- architecture/docs foundation
- governance/security foundation
- shared contract baseline v0.1 (in `main`)
- Java OMS pure-domain state-machine skeleton (in `main`, Candidate 2)
- Java Risk Gateway pure-domain skeleton (Candidate 3 branch, not yet in
  `main`)

### 아직 없음

- Python deterministic backtest
- cross-language compatibility test
- paper broker
- exchange adapter
- paper runtime
- Risk Gateway를 실제로 호출하는 runtime 주문 경로 (OMS/Risk Gateway 통합은
  이후 작업)
- production numeric risk rule (leverage/notional/exposure/loss-limit)
- kill switch

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
- exact exchange symbol, position mode, margin mode는 아직 확정하지 않는다.
- Candidate 2의 `OrderRegistry`는 단일 프로세스 in-memory 경계만 제공한다.
  `docs/06_VALIDATION_POLICY.md` §5의 stale order 처리, restart 후 retry
  idempotency, WebSocket/REST reconciliation, position-side 검증,
  leverage/margin 검증, kill switch 거부는 이후 Candidate로 defer한다.
- Candidate 3의 `RiskRejectReason`은 closed Java enum이 되었지만 3개
  값(`RISK_CONFIGURATION_MISSING`, `RISK_ENGINE_ERROR`, `RISK_STATE_DEGRADED`)
  으로 의도적으로 최소화했다. 실제 production numeric risk rule(leverage,
  notional, exposure, loss-limit)과 그에 대응하는 reason은 이후 Candidate에서
  R4 검토를 거쳐 추가한다.
- Candidate 3의 `RiskGateway`는 아직 어떤 runtime 주문 경로에도 연결되지
  않았다. "모든 주문 후보가 물리적으로 Risk Gateway를 통과해야만 한다"는
  주장은 아직 성립하지 않는다 — OMS/Risk Gateway 통합은 이후 작업.

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
4. Shared contract baseline — 완료, PR #6 (Candidate 1)
5. Java OMS state-machine skeleton — 완료, PR #9 (Candidate 2)
6. Java Risk Gateway skeleton — Candidate 3, 현재 작업 (Issue #10)
7. Python deterministic backtest skeleton — Candidate 4, 시작하지 않음
8. Schema compatibility baseline — Candidate 5
9. Paper broker

## 7. Current task packet — Java Risk Gateway skeleton (Candidate 3, Issue #10)

**Goal:**

Candidate 2의 Java OMS 이후, 최소 pure-domain Java Risk Gateway skeleton을
만든다. 완전한 production risk engine이 아니다.

**Risk class:** R3, R4 hard stop 포함 — 실제 수치 leverage/notional/exposure/
loss-limit 값, kill switch 구현, live/exchange/network 코드, 자격증명,
position/margin mode 결정은 전부 범위 밖.

**In scope:**

- `schemas/v0.1/order-intent.schema.json`과 field-for-field aligned된 typed
  Java `OrderIntent`
- `schemas/v0.1/risk-decision.schema.json`과 field-for-field aligned된 typed
  Java `RiskDecision`
- 최초 closed `RiskRejectReason` enum (최소 3개 값)
- `RiskRule` 추상화, `RiskGateway` fail-closed 집계
- deterministic reason aggregation, deterministic decision metadata

**Out of scope:**

- Candidate 4/5, JSON parser/생성 model, network/exchange/live, 자격증명,
  persistence, 실제 수치 risk-limit 값, kill switch, position/margin 결정,
  OMS 재설계, concurrency infrastructure

**Acceptance criteria:** `docs/claude/PM_HANDOFF.md` §1 "Current candidate"
구현 목록과 Issue #10 본문 참조.

**Required review:**

- `risk-reviewer` — PASS
- `architecture-reviewer` — PASS
- `test-reviewer` — 최초 CHANGES_NEEDED, 수정 후 재검증 최종 PASS
- second AI reviewer policy / CodeRabbit / `security-gates` — PR 생성 후 진행

**Stop conditions:**

- 미확정 product decision을 확정해야 하는 경우
- 실제 수치 risk-limit/leverage/loss 값이 필요한 경우 → DECISION REQUIRED
- shared contract breaking change가 필요한 경우

## 8. Next task packet (reference only, not started) — Python deterministic backtest skeleton (Candidate 4)

이 항목은 순서 참고용 문서일 뿐이며, Candidate 4 issue/branch/PR은 아직
생성하지 않았다. Candidate 3 PR이 merge된 이후에 별도 실행에서 다룬다.

- **Goal:** Python Research Plane의 최소 deterministic backtest skeleton.
  Python은 직접 live order를 넣지 않는다는 경계를 유지한다.
- **Risk class:** R3 — Python 코드이며 live 실행 경로가 아니다.
- **명시적으로 시작하지 않음.**

## 9. Handoff update rule

업데이트 시점:

- 의미 있는 PR이 merge된 후
- major decision이 decision log에 기록된 후
- blocker 또는 phase가 바뀐 후

금지:

- source-of-truth 결정을 독자적으로 변경
- secret 또는 실제 account value 기록
- raw private operation data 기록
