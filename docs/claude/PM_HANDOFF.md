# PM Handoff

**Status:** supporting reference / current snapshot (living document, not source of truth)
**Last verified base main SHA:** `39867fe7f6cfcdcde89196d29c995c480d6ef6f2`
**Repository visibility:** public
**Current phase:** Candidate 4 — Python deterministic backtest skeleton

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
- Candidate 3 / Issue #10 — Java Risk Gateway skeleton. **PR #11 merged**
  (merge commit `39867fe7f6cfcdcde89196d29c995c480d6ef6f2`, 2026-07-08T04:57:55Z).
  `com.ptengine.contract.OrderIntent`, `com.ptengine.risk.RiskDecision` (+
  `RiskOutcome`, `InvalidRiskDecisionException`), `com.ptengine.risk.RiskRejectReason`
  (closed enum, 3 values), `com.ptengine.risk.RiskRule`,
  `com.ptengine.risk.RiskGateway` (+ `RiskDecisionMetadata`) fail-closed
  aggregator. `risk-reviewer` PASS, `architecture-reviewer` PASS,
  `test-reviewer` initial CHANGES_NEEDED → fixed → final PASS. Post-merge, a
  CodeRabbit inline finding (`RiskDecision`'s `Unknown reasonCode` branch
  didn't preserve the original `IllegalArgumentException` as cause) was fixed
  in a follow-up commit before merge; `InvalidRiskDecisionException` now has a
  `(String, Throwable)` constructor. All in `main`.

### Current candidate

Candidate 4 / Issue #12 — Python deterministic backtest skeleton.

**상태:** PR #13 open, review correction 반영 완료, merge 대기 중. `main`에는
아직 merge되지 않았다. Candidate 5는 시작하지 않았다.

현재 branch(`worktree-python-deterministic-backtest-skeleton`, base
`39867fe`)에 다음이 구현되어 있다:

- `python/ptengine/backtest/model.py` — `Candle`(OHLC/timestamp invariant
  강제), `validate_candle_sequence`(non-chronological/overlap/duplicate
  window 거부), `TargetPosition`(LONG/SHORT/FLAT closed enum),
  `FillAction`, `BacktestMetadata`(caller-supplied deterministic id, 숨은
  clock/random/UUID 없음), `BacktestConfig`(synthetic fixture 값, 실제
  BingX 수치/leverage/margin 없음), `Fill`, `EquityPoint`,
  `BacktestResult`. 전부 `decimal.Decimal` 기반, `dataclass(frozen=True)`.
- `python/ptengine/backtest/strategy.py` — `Strategy` Protocol(closed-candle
  history만 받음, future candle/network/live-order 불가능), 시스템 검증용
  `SmaCrossoverStrategy`(수익성 주장 없음, deterministic, stateless).
- `python/ptengine/backtest/engine.py` — `run_backtest`: next-bar-open
  체결(캔들 i 종가에서 생성된 signal은 캔들 i+1 시가에서만 체결, 마지막
  캔들의 signal은 체결되지 않음), adverse slippage(bps), notional 기반
  수수료, 최소 주문 수량 미달 시 스킵+`rejected_signal_count` 기록, LONG↔SHORT
  flip 시 close/open 분리 체결(별도 fee), deterministic linear PnL 정산
  (leverage/margin/funding/liquidation 없음), 미청산 포지션은 마지막 캔들
  종가로 mark-to-market(강제 청산 fee 없음). `total_return`,
  `benchmark_return`(buy-and-hold), `max_drawdown`(peak-to-trough),
  `turnover`(gross executed notional / initial equity) 공식 전부 docstring에
  명시.
- `python/ptengine/backtest/evaluation.py` — `run_in_sample_out_of_sample`:
  IS/OOS 세그먼트 각각 chronological 검증, 겹침·역순 거부, **OOS는 IS 종료
  시점보다 엄격히 나중에(strictly after) 시작해야 함 — 경계 동일(touching)
  케이스도 reject**, 동일 config로 별도 실행, 결과 분리 반환. 파라미터
  피팅/선택/walk-forward 없음 — separation baseline이지 연구 플랫폼이 아님.
- `python/tests/backtest/*.py` — 59 unittest 테스트, 전부 pass. Candidate 4
  issue #12에 명시된 27개 필수 케이스를 모두 포함하고, `test-reviewer` 1차
  CHANGES_NEEDED에서 지적된 6개 gap(최소주문수량 경계값, slippage 적용 후
  가격 기준 fee 정확값, SHORT mark-to-market 중간 검증, out-of-sample 빈
  세그먼트 대칭 케이스, single-candle run, 다중 rejection 누적, 그리고
  tautological했던 IS/OOS state-isolation 테스트를 실제 stateful
  `CountingStrategy`로 재작성)까지 추가해 52→58개로 확장했으며, review
  correction round에서 IS/OOS strict-after 경계 수정에 맞춰 58→59개로 다시
  확장함(아래 review correction 참고).
- `.github/workflows/security-gates.yml`에 최소 스텝 1개 추가:
  `PYTHONPATH=python python -m unittest discover -s python/tests -p "test_*.py" -v`.
  새 workflow 없음, 기존 스텝 제거/약화 없음.
- `python-research-reviewer` — **PASS**. lookahead/데이터 누수 없음,
  fee/slippage/최소주문수량 반영 확인, Python 직접 live order 경로 없음
  확인. 잔여 관찰(블로킹 아님): 데이터 스누핑 실험 추적은 이 skeleton
  범위 밖(ML 단계에서 필요), `decimal.Context` 격리 미구현(현재 default
  context 하에서는 완전히 deterministic).
- `test-reviewer` — 최초 **CHANGES_NEEDED**(경계값·정확값·상태격리 커버리지
  gap 6건, 그 중 1건은 tautological 테스트)를 지적했고, 전부 수정함(52→58개
  테스트). 재검증에서 프로덕션 코드(`engine.py`, `evaluation.py`)가 byte-for-byte
  무변경임을 확인하고, 7개 수정 전부를 실제 arithmetic/control-flow 추적으로
  검증한 뒤 **최종 PASS**.

### PR #13 review correction round

PR #13 오픈 후 CodeRabbit이 두 가지를 지적함:

1. **`evaluation.py`의 IS/OOS 경계 조건**: 원래
   PM task packet은 "OOS가 IS보다 엄격히 나중에 시작"을 요구했는데, 구현은
   `out_of_sample_start_ms < in_sample_end_ms`(reject)를 써서 경계 동일
   (touching) 케이스를 실수로 허용하고 있었다. 이것은 **실제로 유효한 PM
   acceptance mismatch**로 판단해 수정함: 조건을 `<=`(reject)로 강화하고,
   docstring/에러 메시지도 "strictly after, equality rejected"로 정정.
   `test_evaluation.py`에 경계-동일 reject 회귀 테스트를 추가하고, 기존
   touching-boundary accept 테스트를 삭제(그 자리를 대체), strictly-later
   accept 테스트를 신설, state-isolation 테스트의 OOS 시작 시각도 새 규칙에
   맞게 조정 — 58→59개 테스트.
2. `engine.py`의 LONG↔SHORT flip 중 open-leg만 최소주문수량으로 거부되는
   시나리오에 대한 회귀 테스트 요청 — **코드를 직접 추적해 unreachable로
   판정**: `BacktestConfig`는 run 전체에서 불변이며, `current_position`이
   FLAT을 벗어나는 유일한 경로가 `order_quantity >= minimum_order_quantity`
   를 통과한 open뿐이므로, 기존 포지션이 존재한다는 것 자체가 그 run에서
   `order_quantity >= minimum_order_quantity`임을 이미 증명한다. 따라서 이미
   보유한 포지션의 flip open이 같은 최소수량 조건으로 실패하는 상태는
   `run_backtest`를 통해 도달 불가능함. CodeRabbit이 제안한 테스트 fixture
   (`qty=1, min=2`) 자체도 첫 LONG open이 이미 거부되어 제안된 시나리오에
   도달하지 못함을 직접 추적으로 확인. **코드/테스트 변경 없이 근거를 담아
   thread에 답변**, thread는 수동으로 resolve하지 않음.

`engine.py`, `model.py`, `strategy.py`는 이 correction round에서 무변경.
변경은 `evaluation.py`, `test_evaluation.py`, `docs/claude/PM_HANDOFF.md`로
한정. 두 필수 reviewer(`python-research-reviewer`, `test-reviewer`) 모두
correction round를 재검토해 **최종 PASS**.

Candidate 4는 표준 라이브러리만 사용(pandas/numpy/pytest 등 third-party
의존성 추가 없음). `java/**`, `schemas/**` 변경 없음. Python↔Java 일치성
검증 방법(`docs/10_OPEN_QUESTIONS_AND_RISKS.md` 항목 7)은 계속 미확정으로
유지했다 — 이 candidate가 임의로 확정하지 않았다.

## 2. Current code maturity

### 있음

- architecture/docs foundation
- governance/security foundation
- shared contract baseline v0.1 (in `main`)
- Java OMS pure-domain state-machine skeleton (in `main`, Candidate 2)
- Java Risk Gateway pure-domain skeleton (in `main`, Candidate 3)
- Python deterministic backtest skeleton (Candidate 4 branch, not yet in
  `main`)

### 아직 없음

- cross-language compatibility test
- paper broker
- exchange adapter
- paper runtime
- Risk Gateway를 실제로 호출하는 runtime 주문 경로 (OMS/Risk Gateway 통합은
  이후 작업)
- production numeric risk rule (leverage/notional/exposure/loss-limit)
- kill switch
- Python backtest 결과를 소비하는 deployment candidate manifest 생성 경로

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
- Candidate 4의 `python/ptengine/backtest`는 표준 라이브러리(`decimal`,
  `dataclasses`, `enum`, `typing`)만 사용하는 pure-domain 엔진이다. 실제
  데이터 수집/저장, 리포트 생성, 다중 심볼, funding fee, liquidation,
  walk-forward는 전부 이후 작업으로 defer했다.
- Candidate 4는 Python backtest 결과와 Java Risk Gateway/OMS 사이의 실제
  일치성을 검증하지 않는다 — `docs/10_OPEN_QUESTIONS_AND_RISKS.md` 항목 7은
  계속 미확정이며 Candidate 5의 몫이다.
- Candidate 4의 `decimal.Context`는 격리되어 있지 않다(`decimal.localcontext()`
  미사용). 현재 이 skeleton은 독립 프로세스로 실행되므로 완전히
  deterministic하지만, 향후 다른 라이브러리가 전역 `decimal` context를
  변경하는 더 큰 프로세스에 embed될 경우 이론적으로 영향을 받을 수 있다 —
  `python-research-reviewer`가 지적한 non-blocking 관찰.

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
6. Java Risk Gateway skeleton — 완료, PR #11 (Candidate 3)
7. Python deterministic backtest skeleton — Candidate 4, 현재 작업 (Issue #12)
8. Schema compatibility baseline — Candidate 5, 시작하지 않음
9. Paper broker

## 7. Current task packet — Python deterministic backtest skeleton (Candidate 4, Issue #12)

**Goal:**

Candidate 3의 Java Risk Gateway 이후, Python Research Plane의 최소
deterministic backtest skeleton을 만든다. 완전한 production futures
simulator가 아니다.

**Risk class:** Python backtest tier (`docs/09_CLAUDE_WORKFLOW.md` §C) —
plan-first, Python tests 필수, `docs/06_VALIDATION_POLICY.md` §4 편향
체크리스트 필수. second AI reviewer는 이 tier에 자동으로 요구되지 않는다.

**In scope:**

- `python/ptengine/backtest/**` — Candle/TargetPosition/BacktestMetadata/
  BacktestConfig/Fill/BacktestResult 도메인 모델, 순수 strategy 경계, SMA
  crossover 시스템 검증 전략, next-bar-open 체결, 수수료/슬리피지/최소주문
  수량, deterministic 지표(total return/benchmark/drawdown/turnover),
  명시적 IS/OOS 분리 평가 API
- `python/tests/backtest/**` — 27개 이상 필수 회귀 테스트
- `.github/workflows/security-gates.yml`에 최소 CI 스텝 추가

**Out of scope:**

- Candidate 5, `java/**`, `schemas/**`, Python 직접 live order 경로, 거래소
  API/network, leverage/margin/funding/liquidation, kill switch, paper
  broker/runtime, 자격증명, parameter sweep, walk-forward, ML, notebook,
  database

**Acceptance criteria:** `docs/claude/PM_HANDOFF.md` §1 "Current candidate"
구현 목록과 Issue #12 본문 참조.

**Required review:**

- `python-research-reviewer` — PASS
- `test-reviewer` — 최초 CHANGES_NEEDED, 수정 후 재검증 최종 PASS
- CodeRabbit / `security-gates` — PR 생성 후 진행

**Stop conditions:**

- 미확정 product decision을 확정해야 하는 경우
- 실제 BingX 수치(최소 주문 단위, 심볼 등)를 발명해야 하는 경우
- `java/**` 또는 `schemas/**` 변경이 필요해 보이는 경우
- Python↔Java 일치성 검증 방법을 이 작업에서 결정해야 하는 경우

## 8. Next task packet (reference only, not started) — Schema compatibility baseline (Candidate 5)

이 항목은 순서 참고용 문서일 뿐이며, Candidate 5 issue/branch/PR은 아직
생성하지 않았다. Candidate 4 PR이 merge된 이후에 별도 실행에서 다룬다.

- **Goal:** Python OrderIntent와 Java OrderIntent parsing 일치성, cross-language
  schema compatibility baseline.
- **Risk class:** R2 — schema/architecture contract.
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
