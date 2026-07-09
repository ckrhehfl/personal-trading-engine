# PM Handoff

**Status:** supporting reference / current snapshot (living document, not source of truth)
**Last verified base main SHA:** `c69e7de53f8b8ace4e3eaff12eca67c5d1241672`
**Repository visibility:** public
**Current phase:** Candidate 7 — DeploymentManifest schema baseline

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
- Candidate 4 / Issue #12 — Python deterministic backtest skeleton. **PR #13
  merged** (merge commit `b4dc10697be83be6f310fed33de2854a6f51cb69`,
  2026-07-08T07:05:33Z). `python/ptengine/backtest/**` deterministic
  next-bar-open backtest engine (`Candle`/`TargetPosition`/`BacktestConfig`/
  `BacktestResult`, fee/slippage/minimum-order-quantity, IS/OOS strict-after
  separation), 59 unittest tests, `security-gates` wired with a Python
  backtest test step. `python-research-reviewer` PASS, `test-reviewer`
  initial CHANGES_NEEDED → fixed → final PASS. `java/**`/`schemas/**`
  unchanged. Does not verify Python↔Java compatibility (deferred to
  Candidate 5). All in `main`.
- Candidate 5 / Issue #14 — Schema compatibility baseline. **PR #15 merged**
  (approved head `f2cb529a8e8a8d704cb312a5fda5db1e1ae57f16`, resulting `main`
  baseline `cdde7d8a187df8778a8ba48fb2a57a1d48b1b2b4`). Exact claim boundary:
  canonical JSON Schema v0.1 fixtures validate deterministically under the
  existing Python Draft 2020-12 suite, and the Java
  `com.ptengine.contract.json.ContractJsonCodec` JSON boundary accepts/rejects
  and round-trips the **same fixture bytes** consistently. Does **not**
  establish full backtest ↔ Java trading-path behavioral equivalence, signal
  snapshot equivalence, or any paper/live runtime claim —
  `docs/10_OPEN_QUESTIONS_AND_RISKS.md` item 7 remains unresolved. New Java
  package `com.ptengine.contract.json` (`ContractJsonCodec`,
  `ContractJsonException`); pinned dependency
  `com.fasterxml.jackson.core:jackson-databind:2.18.9` added to
  `java/build.gradle` (single pinned version, not dynamic). `RiskDecision`
  reasonCodes validation changed from a closed `RiskRejectReason` wire
  whitelist to open `nonEmptyIdentifier`-shaped strings with explicit
  duplicate rejection (no longer silent dedup); `OrderIntent`/`RiskDecision`
  identifiers gained a shared 128-char bound via the new
  `com.ptengine.contract.ContractLimits.MAX_IDENTIFIER_LENGTH` constant.
  `architecture-reviewer`/`test-reviewer`/`risk-reviewer` all PASS. Java suite
  127/127, Python schema suite 4/4, Python backtest suite 59/59. All in
  `main`.
- Candidate 6 / Issue #16 — Deterministic Java PaperBroker skeleton. **PR #17
  merged** (merge commit `c69e7de53f8b8ace4e3eaff12eca67c5d1241672`,
  2026-07-09T04:29:40Z, three review-correction rounds against CodeRabbit
  findings before merge — see "Candidate 6 detail (historical)" below for the
  full correction history). New pure-domain package `com.ptengine.paper`
  (`PaperMarketSnapshot`, `PaperExecutionMetadata`, `PaperExecutionSide`,
  `PaperExecutionStatus`, `PaperExecutionResult`, `PaperBroker`,
  `InvalidPaperExecutionException`, `PaperValidation`). Given a valid
  `OrderIntent`, a matching `PASS` `RiskDecision`, an explicit
  `PaperMarketSnapshot`, and explicit `PaperExecutionMetadata`,
  `PaperBroker.execute()` returns an equal `PaperExecutionResult` every time.
  Does not establish runtime `Risk → OMS → PaperBroker` wiring, OMS mutation,
  position accounting, reconciliation, partial fills, or live behavior.
  `architecture-reviewer`/`java-oms-reviewer`/`risk-reviewer`/`test-reviewer`
  all final PASS. Java suite 185/185, Python schema suite 4/4, Python
  backtest suite 59/59. All in `main`.

### Candidate 4 detail (historical)

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

### Candidate 5 detail (historical)

Candidate 5 / Issue #14 — Schema compatibility baseline.

**상태:** **PR #15 merged** (approved head
`f2cb529a8e8a8d704cb312a5fda5db1e1ae57f16`, resulting `main` baseline
`cdde7d8a187df8778a8ba48fb2a57a1d48b1b2b4`). Candidate 6(paper broker)는 이
문서 갱신 시점 기준 진행 중(Issue #16) — 아래 "Current candidate" 참고.

**Claim boundary (정확히 이것만 확립함):** canonical JSON Schema v0.1
fixture(`tests/schemas/fixtures/**`)는 기존 Python Draft 2020-12 suite로
deterministic하게 validate되고, Java 타입 JSON contract 경계
(`com.ptengine.contract.json.ContractJsonCodec`)가 **같은 fixture bytes**를
일관되게 accept/reject하고 round-trip한다. 확립하지 **않는** 것: 전체
backtest ↔ Java trading-path 행동 일치성, signal snapshot 일치성, backtest
order-candidate ↔ Risk Gateway runtime 비교, Java strategy runtime 행동,
paper/live runtime 행동. `docs/10_OPEN_QUESTIONS_AND_RISKS.md` 항목 7은
계속 미확정으로 유지한다 — 이 candidate가 해결했다고 주장하지 않는다.

**Python 쪽 artifact(변경 없음):** 기존 `tests/schemas/test_schema_contracts.py`
+ `schemas/v0.1/*.schema.json` + `tests/schemas/fixtures/**`가 그대로
canonical accept/reject oracle이다. 새 Python runtime `OrderIntent`/
`RiskDecision` 모델 없음, `python/ptengine/backtest/**` 변경 없음.

**Java 쪽 artifact(신규):**

- `java/build.gradle`에 pinned 단일 의존성 추가:
  `com.fasterxml.jackson.core:jackson-databind:2.18.9` (dynamic 버전 아님).
- 신규 패키지 `com.ptengine.contract.json`: `ContractJsonCodec`(단일 public
  클래스, `parseOrderIntent`/`parseRiskDecision`/`toJson(OrderIntent)`/
  `toJson(RiskDecision)`) + `ContractJsonException`(단일 전용 예외). 로컬
  JSON만 사용, network/schema registry/code generation 없음. 수동
  `JsonNode`/`ObjectNode` 구성으로 unknown field 거부, trailing token 거부,
  엄격한 decimal lexical 검증(exponent/plus-sign/non-positive 거부, string이
  아니면 거부), epoch millis overflow 거부, LIMIT/MARKET 조건, 결정론적
  직렬화(decimal은 plain base-10 string, MARKET은 `limitPrice` 생략, 반복
  직렬화 byte-for-byte 동일)를 구현. 직렬화 시 canonical decimal 표현으로
  표현 불가능하면 fail-closed(`ContractJsonException`)한다.
- `RiskDecision`: `RiskRejectReason.valueOf()` wire-whitelist 검사와 silent
  dedup(`LinkedHashSet`)을 제거. 대신 `reasonCodes` 각 원소를 open
  `nonEmptyIdentifier` 문자열(non-null/non-blank/최대 128자)로 직접
  검증하고, 중복은 명시적으로 **거부**(order-preserving, 더 이상 silent
  dedup 아님). `decisionId`/`intentId`에도 동일한 최대 128자 상한 추가.
  PASS/BLOCK invariant, immutability는 그대로 유지.
- `RiskRejectReason`: 값 변경 없음(3개 값 그대로) — Javadoc만 이 enum이
  `RiskDecision.reasonCodes()`의 wire whitelist가 **아니며**, 현재 rule이
  실제로 생성하는 닫힌 집합만 문서화한다는 점을 명확히 함.
- `OrderIntent`: `intentId`/`strategyId`/`instrument`에 최대 128자 상한
  추가(기존에는 blank 검사만 있었음). LIMIT/MARKET, positive-decimal 등
  기존 invariant는 무변경.

**Fixture 전략:** `tests/schemas/fixtures/**`의 기존 파일은 전부 무변경.
`tests/schemas/fixtures/order-intent/invalid/`에 6개 신규 invalid fixture만
추가(`whitespace-intent-id`/`identifier-too-long`/`unknown-direction`/
`notional-as-number`/`exponent-notional`/`epoch-millis-overflow`),
`test_schema_contracts.py`의 `EXPECTED_INVALID_KEYWORDS`에 대응 keyword
추가(`pattern`/`maxLength`/`enum`/`type`/`pattern`/`maximum`). 신규 Java
테스트(`SharedFixtureCompatibilityTest`)는 이 fixture 디렉터리를 저장소
경로 그대로 읽는다 — Java 전용 fixture 복사본을 만들지 않았다.

### Candidate 6 detail (historical)

Candidate 6 / Issue #16 — Deterministic Java PaperBroker skeleton.

**상태:** **PR #17 merged** (merge commit
`c69e7de53f8b8ace4e3eaff12eca67c5d1241672`, 2026-07-09T04:29:40Z). PR 오픈
후 CodeRabbit이 세 차례 review round에 걸쳐 findings를 제기했고(fail-closed
`OrderType` switch, `PaperExecutionResult`/`PaperMarketSnapshot`/`PaperBroker`
null-check 예외 타입 통일, 중복 identifier validator 추출, best-price 선택
헬퍼 추출, 그리고 PM이 독자적으로 찾은 missing-risk-decision 예외 불일치 1건)
전부 valid finding으로 확인되어 세 번의 correction commit으로 수정한 뒤
merge되었다. Candidate 7은 이 문서 갱신 시점 기준 진행 중(Issue #18) — 아래
"Current candidate" 참고.

**Claim boundary (정확히 이것만 확립함):** 같은 유효한 `OrderIntent`, 그와
일치하는 `PASS` `RiskDecision`, 명시적 `PaperMarketSnapshot`, 명시적 caller
공급 `PaperExecutionMetadata`가 주어지면 `PaperBroker.execute()`는 매번 동일한
`PaperExecutionResult`를 반환하는 순수 도메인 결정론적 paper 체결 경계.
확립하지 **않는** 것: 실제 `OrderIntent → RiskGateway → OMS → PaperBroker`
runtime 배선, OMS state mutation, 영속 주문 상태, position accounting,
reconciliation, partial fill, 거래소 현실성(BingX 수량/가격 precision),
fee/funding/liquidation 모델, 장기 실행 paper runtime, live 동작.

**신규 패키지:** `com.ptengine.paper` (production: `PaperMarketSnapshot`,
`PaperExecutionMetadata`, `PaperExecutionSide`, `PaperExecutionStatus`,
`PaperExecutionResult`, `PaperBroker`, `InvalidPaperExecutionException`).
기존 `com.ptengine.contract`/`com.ptengine.risk`/`com.ptengine.oms` 파일은
전부 무변경 — 신규 파일만 추가했다. 식별자 최대 길이는 기존
`com.ptengine.contract.ContractLimits.MAX_IDENTIFIER_LENGTH`(Candidate 5에서
도입)를 그대로 재사용한다(처음 구현 시 이 상수의 존재를 놓치고 각 record에
동일한 리터럴 128을 중복 선언했으나, `architecture-reviewer` 1차 리뷰에서
지적되어 재사용으로 수정함).

**리스크 권한 경계:** `PaperBroker.execute(OrderIntent, RiskDecision,
PaperMarketSnapshot, PaperExecutionMetadata)`는 `RiskDecision`이 null이거나,
`outcome() != PASS`이거나, `intentId()`가 `OrderIntent.intentId()`와
불일치하면 항상 예외를 던진다. `RiskGateway`는 호출하지 않는다(순수하게
caller가 이미 계산한 `RiskDecision`을 소비만 한다).

**MARKET/LIMIT 의미:** BUY MARKET은 `bestAsk`, SELL MARKET은 `bestBid`에서
체결. LIMIT은 crossing 시 제출된 `limitPrice`가 아니라 현재 executable
quote(`bestAsk`/`bestBid`)에서 체결하며, 등가(exact equality)는 체결로
취급한다. `FILLED`/`NO_FILL` 두 상태만 존재, `PARTIAL_FILL` 없음.

**Required review:** `architecture-reviewer` — 1차 CHANGES_NEEDED(기존
`ContractLimits.MAX_IDENTIFIER_LENGTH` 재사용 대신 리터럴 128을 3개 파일에
중복 선언, 그리고 `InvalidRiskDecisionException`과 달리
`InvalidPaperExecutionException`에 `(message, cause)` 생성자 누락) → 수정 후
재검증 최종 **PASS**. `java-oms-reviewer` — **PASS**(OMS 미개입,
RiskGateway 우회 없음, MARKET/LIMIT 가격 로직 라인 단위 추적 확인).
`risk-reviewer` — **PASS**(수치 leverage/notional/loss-limit 값 없음, R4
아님, fail-closed 4개 분기 전부 추적 확인). `test-reviewer` — **PASS**(45개
필수 케이스 전부 실제 비-tautological assertion으로 커버 확인).

**검증:** Python schema suite 4/4 pass(무변경), Python backtest suite 59/59
pass(무변경), Java Gradle suite 175/175 pass(신규 48개: `PaperBrokerTest` 22
+ `PaperMarketSnapshotTest` 12 + `PaperExecutionResultTest` 9 +
`PaperExecutionMetadataTest` 5, 기존 127개 무변경).

### Current candidate

Candidate 7 / Issue #18 — DeploymentManifest schema baseline.

**상태:** 구현 완료, 로컬 검증 진행 중. `main`에는 아직 merge되지 않았다.
Candidate 8은 시작하지 않았다.

**Claim boundary (정확히 이것만 확립함):** canonical v0.1 `DeploymentManifest`
schema(`schemas/v0.1/deployment-manifest.schema.json`)는 candidate-only,
non-authorizing shared envelope을 정의한다 — deployment identity,
strategy/version provenance, data/parameter/backtest provenance, target
참조, risk-profile 참조, optional feature/model/previous-deployment 참조.
기존 deterministic Draft 2020-12 fixture suite가 유효한 manifest fixture를
accept하고 무효한 fixture를 예상 validator keyword로 reject함을 확립한다.
확립하지 **않는** 것: Python manifest generator, Java manifest
parsing/loading, Python↔Java typed manifest compatibility, artifact
existence/checksum 검증, risk-profile existence/content 검증, paper/canary/
live deployment, live 승인, rollback 실행, runtime config loading, 정확한
거래소 API symbol mapping.

**Safety boundary:** manifest schema는 shape validation일 뿐이다 — 실행
권한, risk 승인, live 승인, 참조된 artifact/backtest/risk-profile의
존재·유효성 증명이 아니다. v0.1 baseline에서 `status`는 `CANDIDATE`만
허용한다. leverage/notional/exposure/loss-limit, order policy, margin/
position mode, 정확한 거래소 API symbol, 자격증명, artifact 경로/URL은 이
schema에 없다.

**신규 파일:** `schemas/v0.1/deployment-manifest.schema.json`,
`tests/schemas/fixtures/deployment-manifest/**`(valid 2개, invalid 11개).
`tests/schemas/test_schema_contracts.py`, `schemas/README.md`,
`docs/07_MLOPS_AUTO_TRAIN_DEPLOY_ROLLBACK.md`,
`docs/10_OPEN_QUESTIONS_AND_RISKS.md`, `docs/11_DECISION_LOG.md`(D012)를
최소 변경. 기존 `common.schema.json`/`order-intent.schema.json`/
`risk-decision.schema.json`은 전부 무변경. `java/**`,
`python/**`(schema harness 제외) 변경 없음.

**Required review:** `architecture-reviewer`, `risk-reviewer`,
`test-reviewer` — 결과는 PR 생성 후 본 문서에 갱신한다.

## 2. Current code maturity

### 있음

- architecture/docs foundation
- governance/security foundation
- shared contract baseline v0.1 (in `main`)
- Java OMS pure-domain state-machine skeleton (in `main`, Candidate 2)
- Java Risk Gateway pure-domain skeleton (in `main`, Candidate 3)
- Python deterministic backtest skeleton (in `main`, Candidate 4)
- Java ↔ shared-fixture JSON-boundary compatibility codec (in `main`,
  Candidate 5) — see the claim-boundary note under §1 "Candidate 5 detail";
  this is not full backtest ↔ Java runtime equivalence
- Java deterministic paper-execution pure-domain skeleton, `com.ptengine.paper`
  (in `main`, Candidate 6) — see the claim-boundary note under §1 "Candidate
  6 detail"; not wired into any runtime order path, no OMS mutation, no
  position accounting
- canonical v0.1 `DeploymentManifest` JSON Schema baseline,
  `schemas/v0.1/deployment-manifest.schema.json` (Candidate 7 branch, not yet
  in `main`) — see the claim-boundary note under §1 "Current candidate";
  candidate-only, non-authorizing shape contract only, no generator/loader

### 아직 없음

- full backtest ↔ Java trading-path behavioral equivalence (Candidate 5 는
  JSON-boundary compatibility만 확립함; `docs/10_OPEN_QUESTIONS_AND_RISKS.md`
  항목 7은 계속 미확정)
- exchange adapter
- paper runtime (Candidate 6은 순수 도메인 `PaperBroker.execute()` 계산만
  제공하며, 장기 실행 runtime이나 OMS/Risk Gateway 배선은 아니다)
- Risk Gateway를 실제로 호출하는 runtime 주문 경로, 그리고 Risk → OMS →
  PaperBroker 배선 (OMS/Risk Gateway/PaperBroker 통합은 이후 Candidate)
- production numeric risk rule (leverage/notional/exposure/loss-limit)
- kill switch
- deployment manifest generator(Python)/loader(Java)/runtime — Candidate 7은
  shape validation만 제공하며, Python이 manifest를 생성하거나 Java가 이를
  읽는 실제 구현은 존재하지 않는다

## 3. Operating constraints

- Python Research Plane과 Java Trading Plane 책임을 분리한다.
- 모든 runtime 구현은 shared contract를 기준으로 한다.
- 한 PR은 Python only / Java only / schema only를 기본으로 한다.
- R2/R3 변경은 plan-first다.
- owner가 final merge action을 수행한다. Claude Code는 merge하지 않는다.
- 새 commit이 생기면 latest-head review completeness를 다시 확인한다.

## 4. Known residual risks / technical debt

Candidate 1~7을 막지 않는 backlog:

1. PreToolUse detector와 CI detector의 일부 parity gap
2. machine-readable canonical policy configuration 부재
3. project guard가 완전한 sandbox는 아님
4. reviewer auto-routing 부재
5. second AI reviewer 자동 enforcement 부재
6. GitHub server-side protection의 현재 상태는 다음 고위험 변경 전 재확인 필요

추가로:

- shared contract v0.1은 언어별 generated model을 제공하지 않는다.
- Candidate 5는 shared JSON fixture에 대한 Java `ContractJsonCodec`의
  parse/serialize round-trip만 검증한다. 전체 backtest ↔ Java trading-path
  행동 일치성, signal snapshot 일치성은 검증하지 않으며
  `docs/10_OPEN_QUESTIONS_AND_RISKS.md` 항목 7은 계속 미확정이다.
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
- Candidate 5는 `RiskDecision.reasonCodes()` 검증을 `RiskRejectReason`
  closed-enum whitelist에서 schema-aligned open-string 검증(+ 명시적 중복
  거부)으로 바꿨다 — 이것은 `RiskGateway` 자체의 rule 평가 로직이나 risk
  policy 값을 바꾸지 않는, JSON-representation 계약 정합성 수정이다.
  `RiskGateway`가 실제로 방출하는 reason은 여전히 `RiskRejectReason`의 3개
  값뿐이다.
- Candidate 6의 `PaperBroker`는 `RiskDecision`을 authority로 요구하지만
  `RiskGateway`를 호출하지 않는다 — 호출자가 이미 계산한 `RiskDecision`을
  그대로 신뢰한다. 실제 runtime에서 호출자가 진짜 `RiskGateway.evaluate()`
  결과 대신 임의로 합성한 PASS `RiskDecision`을 넘기지 못하도록 하는 배선은
  이후 Candidate(runtime wiring)의 책임이며, 이 skeleton 자체는 그 경로를
  아직 물리적으로 막지 않는다.
- Candidate 6은 EXIT intent를 position 존재 여부와 무관하게 처리한다(position
  state 자체가 없으므로) — 의도된 범위 제한이며, position accounting/
  reconciliation은 이후 Candidate로 defer한다.
- Candidate 7의 `DeploymentManifest` v0.1 schema는 shape validation일
  뿐이다. Python manifest generator, Java manifest loader/parser,
  cross-language typed compatibility test, artifact existence/checksum
  검증, risk-profile existence/content 검증은 전부 이후 Candidate로
  defer한다. `status`는 `CANDIDATE`만 허용하며, 이 schema 자체는 어떤
  paper/canary/live 배포 결정도 내리지 않는다.

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
7. Python deterministic backtest skeleton — 완료, PR #13 (Candidate 4)
8. Schema compatibility baseline — 완료, PR #15 (Candidate 5)
9. Paper broker — 완료, PR #17 (Candidate 6)
10. DeploymentManifest schema baseline — Candidate 7, 현재 작업 (Issue #18)
11. 다음 candidate(BingX adapter skeleton 등, `CLAUDE.md` MVP Priority 순서
    참고) — 시작하지 않음, 정확한 범위는 아직 PM이 확정하지 않았다

## 7. Current task packet — DeploymentManifest schema baseline (Candidate 7, Issue #18)

**Goal:**

Candidate 6 이후, `DeploymentManifest`의 최소 canonical v0.1 JSON Schema
baseline을 만든다. manifest generator, loader, paper runtime, live 배포
동작이 존재하기 전에 Python/Java가 공유할 candidate-only, non-authorizing
shape 계약이다.

**Risk class:** R2 — schema/architecture contract, R4-인접 개념(`CLAUDE.md`가
deployment manifest를 명시적으로 high-risk로 분류). plan-first, 호환성
검증 필수. `risk-reviewer`는 live/leverage/risk-limit/자격증명/거래소-write
동작이 전혀 없어도 필수로 요구된다.

**In scope:**

- `schemas/v0.1/deployment-manifest.schema.json` — 신규 canonical schema
- `tests/schemas/fixtures/deployment-manifest/**` — valid 2개, invalid 11개
- `tests/schemas/test_schema_contracts.py`의 최소 harness 통합
- `schemas/README.md`, `docs/07_MLOPS_AUTO_TRAIN_DEPLOY_ROLLBACK.md`,
  `docs/10_OPEN_QUESTIONS_AND_RISKS.md`, `docs/11_DECISION_LOG.md`(D012),
  `docs/claude/PM_HANDOFF.md` 정렬

**Out of scope:**

- Python manifest generator, Java manifest parsing/loading/model, Python↔Java
  typed manifest compatibility test, artifact existence/checksum 검증,
  risk-profile existence/content 검증, paper/canary/live deployment, live
  승인, rollback 실행, runtime config loading, 정확한 거래소 API symbol
  mapping, `java/**`/`python/**`(schema harness 제외)/`common.schema.json`/
  `order-intent.schema.json`/`risk-decision.schema.json` 변경, Candidate 8

**Acceptance criteria:** Issue #18 본문 참조.

**Required review:**

- `architecture-reviewer`, `risk-reviewer`, `test-reviewer` — 결과는 PR
  생성 후 본 문서에 갱신한다.

**Stop conditions:**

- main이 검증된 base SHA와 다른 경우
- 다른 Candidate 7 issue/branch/PR이 이미 존재하는 경우
- 기존 v0.1 schema semantics 변경이 필요한 경우
- schema version bump가 필요한 경우
- Python generator 또는 Java loader/codec/model 코드가 필요해 보이는 경우
- 실제 artifact 경로/URL/checksum semantics 결정이 필요한 경우
- 실제 risk-profile 내용/수치 한도 결정이 필요한 경우
- leverage/risk/market-order/live 필드가 필요한 경우
- 정확한 거래소 API symbol 결정이 필요한 경우
- margin/position mode 결정이 필요한 경우
- PAPER/CANARY/LIVE 상태가 필요한 경우
- runtime deployment/rollback 동작이 필요한 경우
- Candidate 8 scope가 필요한 경우

## 7a. Historical task packet — Deterministic Java PaperBroker skeleton (Candidate 6, Issue #16)

**Goal:**

Candidate 5 이후, 이미 유효한 `OrderIntent`를 그와 일치하는 `PASS`
`RiskDecision`이 동반될 때만 명시적 `PaperMarketSnapshot` 하나에 대해
시뮬레이션할 수 있는 최소 deterministic Java `PaperBroker` skeleton을
만든다. 전체 runtime 주문 경로 배선, OMS mutation, position 관리,
reconciliation, 거래소 접근은 확립하지 않는다.

**Risk class:** R3 — runtime trading code. plan-first, 도메인별 테스트 필수,
네 reviewer(`architecture-reviewer`/`java-oms-reviewer`/`risk-reviewer`/
`test-reviewer`) 요구.

**In scope:**

- 신규 패키지 `com.ptengine.paper.**` (production + test): `PaperMarketSnapshot`,
  `PaperExecutionMetadata`, `PaperExecutionSide`, `PaperExecutionStatus`,
  `PaperExecutionResult`, `PaperBroker`, `InvalidPaperExecutionException`
- `PaperBroker.execute(OrderIntent, RiskDecision, PaperMarketSnapshot,
  PaperExecutionMetadata)` — risk 권한 경계, MARKET/LIMIT 가격 로직, 결과
  불변식

**Out of scope:**

- `com.ptengine.contract`/`com.ptengine.risk`/`com.ptengine.oms` 기존 파일
  변경, `schemas/v0.1/*.schema.json`, `python/**`, runtime Risk→OMS→
  PaperBroker 배선, OMS mutation, position accounting/reconciliation,
  partial fill, 거래소 수량/가격 precision, fee/funding/liquidation, live
  동작, Candidate 7

**Acceptance criteria:** `docs/claude/PM_HANDOFF.md` §1 "Candidate 6 detail
(historical)" 구현 목록과 Issue #16 본문 참조. **완료, PR #17 merged.**

**Required review:**

- `architecture-reviewer` — 1차 CHANGES_NEEDED → 수정 후 최종 **PASS**
- `java-oms-reviewer` — **PASS**
- `risk-reviewer` — **PASS**
- `test-reviewer` — **PASS**
- CodeRabbit / `security-gates` — 세 차례 review-correction round 후 통과,
  merge 완료

**Stop conditions:**

- PaperBroker가 position state를 요구하는 것처럼 보이는 경우
- partial-fill/유동성 depth 로직이 필요해 보이는 경우
- 거래소 수량/가격 precision, contract size, symbol mapping을 발명해야 하는
  경우
- BingX/network/API 접근이 필요해 보이는 경우
- position mode/margin mode/leverage/risk-limit policy 변경이 필요한 경우
- 기존 `OrderIntent`/`RiskDecision`/`RiskGateway`/OMS production 의미를
  변경해야 하는 경우
- runtime 배선이나 OMS mutation이 필요해 보이는 경우

## 7b. Historical task packet — Schema compatibility baseline (Candidate 5, Issue #14)

**Goal:**

Candidate 4의 Python deterministic backtest 이후, canonical JSON Schema
v0.1 fixture와 Java 타입 contract 경계 사이의 최소 deterministic
cross-language compatibility baseline을 만든다. 전체 backtest ↔ Java
trading-path 행동 일치성은 확립하지 않는다.

**Risk class:** R2 — schema/architecture contract. `RiskDecision` 계약
의미를 바꾸므로 `architecture-reviewer`/`test-reviewer`/`risk-reviewer`
모두 요구.

**In scope:**

- `java/build.gradle`(pinned Jackson 의존성 1개), 신규
  `com.ptengine.contract.json.ContractJsonCodec` + `ContractJsonException`
- `RiskDecision`/`RiskRejectReason`(open-string 정렬, Javadoc 정정),
  `OrderIntent`(식별자 최대 128자)
- `tests/schemas/fixtures/order-intent/invalid/`에 6개 신규 fixture,
  `test_schema_contracts.py` keyword mapping 갱신
- 신규 Java 테스트: codec 단위 테스트 2개 파일 + shared-fixture
  compatibility 테스트 1개 파일

**Out of scope:**

- `schemas/v0.1/*.schema.json`, `python/ptengine/backtest/**`, Candidate 6,
  paper broker, exchange adapter, network/HTTP, 자격증명, live/leverage/
  risk-policy 변경, OMS/Risk Gateway runtime wiring, open question #7 해결

**Acceptance criteria:** Issue #14 본문 참조(완료, PR #15 merged).

**Required review:**

- `architecture-reviewer` — PASS
- `test-reviewer` — PASS
- `risk-reviewer` — PASS

## 7c. Historical task packet — Python deterministic backtest skeleton (Candidate 4, Issue #12)

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

## 8. Next task packet (reference only, not started) — Candidate 8

Candidate 7(DeploymentManifest schema baseline, Issue #18)의 PR이 merge된
이후 별도 실행에서 다룰 다음 candidate다. `CLAUDE.md`의 MVP Priority
순서(스키마 → Java OMS → Risk Gateway → Python backtest → schema
compatibility → paper broker → **BingX adapter skeleton** → paper trading →
canary live)를 참고하면 다음은 BingX adapter skeleton 방향일 가능성이
높지만, 정확한 scope/issue 번호/제목은 아직 PM이 확정하지 않았다.
Candidate 8 issue/branch/PR은 아직 생성하지 않았다.

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
