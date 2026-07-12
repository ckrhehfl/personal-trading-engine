# MVP_SCOPE_AND_ROADMAP.md

# MVP 범위 및 로드맵 v3

---

## 1. MVP 원칙

MVP는 수익 전략을 완성하는 단계가 아니다. MVP의 목표는 안전하게 실험하고, 검증하고, 멈출 수 있는 구조를 만드는 것이다.

```text
수익 전략보다 먼저:
데이터 → 백테스트 → 리스크 → OMS → paper trading → live guard
```

---

## 1A. 구현 상태 용어 (Candidate 15 reconciliation)

Candidate 15부터 이 로드맵의 모든 항목은 아래 4개 상태 라벨 중 정확히
하나로 분류한다. 라벨은 그대로 사용하며 새 라벨을 만들지 않는다.

- **IMPLEMENTED_BASELINE** — production/schema 동작이 실제로 존재하고,
  deterministic 테스트가 존재하며, 서술이 그 구현된 경계 안에만 머무른다.
- **PARTIAL** — 더 넓은 로드맵 항목에 대해 skeleton, single-case
  projection, test-only composition, aligned-input equivalence, 또는
  non-operational report 수준의 증거만 존재한다.
- **NOT_IMPLEMENTED** — 저장소 어디에도 해당 capability를 제공하는 구현이
  없다.
- **DECISION_REQUIRED** — 정직한 구현 전에 문서화된 미확정 선택이 먼저
  필요하다.

전체 프로젝트 상태에는 `COMPLETE`를 쓰지 않는다. `production-ready`,
`paper-ready`, `live-ready`, `canary-ready`도 쓰지 않는다.

다음 구분은 이 문서 전체에서 강제된다: unit/integration test ≠ 경과한
paper 운영, pure-domain component ≠ service/runtime, deterministic
caller-supplied snapshot ≠ 거래소 market-data 수집, `toPlainText` report ≠
alert delivery, in-memory 중복 방지 ≠ restart-safe idempotency, 1건의
execution projection ≠ position ledger, 1건의 reconciliation 비교 ≠
continuous reconciliation service, MARKET aligned-input equality ≠ 전체
backtest/live equivalence, schema 존재 ≠ manifest generation/consumption,
live-disabled policy ≠ 구현된 kill switch.

---

## 1B. Candidate 1–14 구현 체크포인트

아래 표는 지금까지 merge된 각 Candidate가 실제로 `main`에 어떤 증거를
남겼는지를 코드/스키마/테스트 경로 기준으로 기록한다. Candidate 번호
자체는 진행 순서를 나타낼 뿐 완성도를 의미하지 않는다.

| Candidate (PR) | 항목 | 상태 | 증거 경로 | 경계 / 증명되지 않은 것 |
|---|---|---|---|---|
| 1 (#6) | Shared contract baseline | IMPLEMENTED_BASELINE | `schemas/v0.1/{common,order-intent,risk-decision}.schema.json`, `tests/schemas/fixtures/**`, `tests/schemas/test_schema_contracts.py` | Shape validation만 증명. Cross-language JSON codec 호환성은 Candidate 5, `DeploymentManifest`는 Candidate 7의 몫. |
| 2 (#9) | Java OMS state-machine skeleton | IMPLEMENTED_BASELINE | `java/src/main/java/com/ptengine/oms/{OrderState,Order,OrderRegistry,OrderTransitions,IllegalOrderTransitionException,DuplicateClientOrderIdException}.java`, `OrderLifecycleTest`, `OrderRegistryTest` | 단일 프로세스 in-memory skeleton. fill-quantity aggregation, restart/retry idempotency, WebSocket/REST reconciliation, leverage/margin/position-side 검증, kill-switch 연동 전부 미구현(`OrderState.java` Javadoc에 명시). |
| 3 (#11) | Java Risk Gateway skeleton | IMPLEMENTED_BASELINE | `java/.../risk/{RiskDecision,RiskDecisionMetadata,RiskGateway,RiskOutcome,RiskRejectReason,RiskRule,InvalidRiskDecisionException}.java`, `RiskGatewayTest`, `RiskDecisionTest` | Fail-closed pure-domain aggregator. `RiskRejectReason`은 3개 값만 존재하고 `RISK_STATE_DEGRADED`는 아직 어떤 rule도 발생시키지 않는 예약값. Production numeric risk rule(leverage/notional/exposure/loss-limit) 없음. 이 시점엔 어떤 runtime 주문 경로에도 연결되지 않음(Candidate 8에서 연결). |
| 4 (#13) | Python deterministic backtest skeleton | IMPLEMENTED_BASELINE | `python/ptengine/backtest/{model,engine,strategy,evaluation}.py`, `python/tests/backtest/{test_engine,test_evaluation,test_metrics,test_model,test_strategy}.py` | Next-bar-open deterministic engine, 수수료/슬리피지/최소주문수량, IS/OOS 분리 포함. `SmaCrossoverStrategy`는 명시적으로 수익성 주장이 아님. Live/exchange 경로 없음. |
| 5 (#15) | Schema compatibility baseline | PARTIAL | `java/.../contract/json/ContractJsonCodec.java`, `OrderIntentJsonCodecTest`, `RiskDecisionJsonCodecTest`, `SharedFixtureCompatibilityTest` | JSON-boundary codec이 공유 fixture와 호환됨만 증명. 코드 자체 Javadoc이 "전체 backtest ↔ Java trading-path 행위 동등성을 증명하지 않는다"고 명시. |
| 6 (#17) | Deterministic Java PaperBroker skeleton | IMPLEMENTED_BASELINE | `java/.../paper/{PaperBroker,PaperMarketSnapshot,PaperExecutionResult,PaperExecutionMetadata,PaperExecutionSide,PaperExecutionStatus,PaperValidation,InvalidPaperExecutionException}.java`, `PaperBrokerTest` 외 | 호출자가 넘긴 intent/riskDecision/marketSnapshot/metadata의 순수 함수. `RiskGateway`를 직접 호출하지 않고, 포지션 상태를 모르고, OMS를 mutate하지 않음. |
| 7 (#19) | DeploymentManifest schema baseline | IMPLEMENTED_BASELINE | `schemas/v0.1/deployment-manifest.schema.json`, `tests/schemas/fixtures/deployment-manifest/**` | 스키마 자체 description이 "candidate-only, non-authorizing... 실행 권한도 리스크 승인도 아니다"라고 명시. Shape validation만. `status`는 `CANDIDATE`만 허용. 어떤 runtime도 아직 이 manifest를 생성/소비하지 않음. |
| M1 (#21) | Merge-gate self-tests를 CI에 편입 | IMPLEMENTED_BASELINE (governance) | `tests/ci/test_security_gates.py`, `tests/claude/test_policy_guard.py`, `.github/workflows/security-gates.yml` | 로드맵 capability가 아니라 governance/CI 인프라. |
| 8 (#23) | Risk → OMS → PaperBroker 통합 | IMPLEMENTED_BASELINE | `java/.../integration/{PaperOrderPipeline,PaperOrderPipelineResult}.java`, `PaperOrderPipelineTest` | 실제 `RiskGateway`+`OrderRegistry`+`PaperBroker`의 in-process composition, PASS 우회 없음. 클래스 자체 Javadoc이 "다른 호출자가 컴포넌트를 직접 호출하는 것이 불가능하다고 주장하지 않는다"고 명시. Persistence/재시작 복구/동시성/포지션·PnL 회계/reconciliation 연동 없음. |
| 9 (#25) | PositionSnapshot reconciliation baseline | IMPLEMENTED_BASELINE | `java/.../reconciliation/{PositionSnapshot,PositionReconciler,PositionReconciliationResult}.java`, `PositionReconcilerTest` | 정확히 하나의 열린 포지션만 모델링(포트폴리오/계좌 원장 아님, flat/no-position 표현 없음). Reconciler는 호출자가 준 두 스냅샷의 순수 field-for-field 비교. Staleness threshold 없음, continuous service 아님. |
| 10 (#27) | PaperExecutionResult → PositionSnapshot 투영 | IMPLEMENTED_BASELINE | `java/.../reconciliation/PaperExecutionPositionProjector.java`, `PaperExecutionPositionProjectorTest` | 정확히 하나의 FILLED `PaperExecutionResult`를 최대 하나의 `PositionSnapshot`으로 매핑. Fill 집계(aggregation) 없음, 기존 포지션 업데이트 없음 — 클래스 Javadoc에 명시. |
| 11 (#29) | Paper order → position reconciliation 통합 테스트 | PARTIAL | `java/.../integration/PaperOrderPositionReconciliationIntegrationTest.java` | 클래스 Javadoc 자체가 "Integration-only proof... Adds no production code"라고 명시 — 이미 merge된 Candidate 8/9/10 컴포넌트의 test-only composition. Runtime service 아님. |
| 12 (#31) | Daily paper report summary | IMPLEMENTED_BASELINE | `java/.../report/{DailyPaperTradingReport,DailyPaperTradingReportGenerator}.java`, `DailyPaperTradingReportGeneratorTest` | 호출자가 이미 만든 pipeline/reconciliation 결과 리스트의 deterministic `toPlainText()` 요약. Clock 없음, persistence 없음, alert sink 없음 — operational report/alert가 아님. |
| 13 (#33) | Python↔Java MARKET-fill equivalence | PARTIAL | `tests/execution/fixtures/python-java-market-fill-equivalence-v0.1.json`, `PythonJavaMarketFillEquivalenceTest.java`, `test_python_java_market_fill_equivalence.py` | 4개 고정 MARKET 케이스에 대한 economic side + 정확한 execution price 일치만 증명. 테스트 자체 Javadoc/docstring은 LIMIT/NO_FILL equivalence만 명시적으로 범위 밖이라고 선언하며, 일반 슬리피지 모델 parity·signal parity·risk-rule parity·runtime parity는 이 감사(Candidate 15)가 추가로 식별한, 아직 증명되지 않은 gap이다. |
| 14 (#35) | Daily paper report exact invariant hardening | IMPLEMENTED_BASELINE | `java/.../report/DailyPaperTradingReport.java`의 invariant 1–10, `DailyPaperTradingReportGeneratorTest` | 기존 non-operational report의 내부 산술 불변식(exact partition/correlation)을 강화. Persistence, scheduling, alert transport는 추가되지 않음. |

---

## 2. MVP v0.1

목표: 기본 구조와 Java OMS skeleton 구축

포함:

- repo 구조
- PRODUCT/SYSTEM/RISK/VALIDATION 문서
- Python backtest skeleton
- 단순 전략 1개
- Java domain model
- Java OrderState machine
- Java RiskDecision model
- Java PaperBroker skeleton
- DeploymentManifest schema
- JSON logs
- unit tests

제외:

- 실제 live order
- 자동학습
- 고급 전략
- 실시간 WebSocket 완성

---

## 2A. MVP v0.1 항목별 상태 (Candidate 15)

| 항목 | 상태 | 근거 |
|---|---|---|
| repo 구조 | IMPLEMENTED_BASELINE | `docs/`, `java/`, `python/`, `schemas/`, `tests/`, `scripts/ci/` 존재 및 CI에서 사용됨 |
| PRODUCT/SYSTEM/RISK/VALIDATION 문서 | IMPLEMENTED_BASELINE | `docs/01_PRODUCT_SPEC.md`, `docs/02_SYSTEM_SPEC.md`, `docs/05_RISK_POLICY.md`, `docs/06_VALIDATION_POLICY.md` |
| Python backtest skeleton | IMPLEMENTED_BASELINE | Candidate 4, `python/ptengine/backtest/{model,engine}.py` |
| 단순 전략 1개 | IMPLEMENTED_BASELINE | `python/ptengine/backtest/strategy.py`의 `SmaCrossoverStrategy` |
| Java domain model | IMPLEMENTED_BASELINE | Candidate 1/3, `com.ptengine.contract.OrderIntent` 외 |
| Java OrderState machine | IMPLEMENTED_BASELINE | Candidate 2, `com.ptengine.oms.OrderState`/`Order`/`OrderTransitions` |
| Java RiskDecision model | IMPLEMENTED_BASELINE | Candidate 3, `com.ptengine.risk.RiskDecision` |
| Java PaperBroker skeleton | IMPLEMENTED_BASELINE | Candidate 6, `com.ptengine.paper.PaperBroker` |
| DeploymentManifest schema | IMPLEMENTED_BASELINE | Candidate 7, `schemas/v0.1/deployment-manifest.schema.json` |
| JSON logs | NOT_IMPLEMENTED | `java/build.gradle`에 SLF4J/Logback 등 로깅 의존성이 없고, `java/src`·`python/ptengine` 전체에 logger 호출이 없다 — 구조화 로깅 자체가 아직 어떤 형태로도 존재하지 않는다 |
| unit tests | IMPLEMENTED_BASELINE | Java(`java/src/test/**`, JUnit 5) + Python(`python/tests/**`, unittest) + schema(`tests/schemas/**`) 스위트 전부 CI(`security-gates.yml`)에 연결 |

MVP v0.1 foundation은 "JSON logs" 한 항목을 제외하고 실질적으로
구축되었다. 이 한 항목이 남아 있다는 사실만으로 v0.1 전체를 미완료로
재분류하지는 않되, v0.1을 "완료"로 선언하지도 않는다 — 표에 있는 그대로
항목별로만 판단한다.

---

## 3. MVP v0.2

목표: Paper trading 가능한 내부 루프

포함:

- BingX market data 수집
- candle 저장
- Python backtest report
- Java paper runtime
- order intent → risk → OMS → paper fill
- position snapshot
- reconciliation test
- daily report
- Telegram alert

---

## 3A. MVP v0.2 항목별 상태 (Candidate 15)

| 항목 | 상태 | 근거 |
|---|---|---|
| BingX market data 수집 | NOT_IMPLEMENTED | `java/src`, `python/ptengine` 전체에 BingX/WebSocket/HTTP client 코드가 없다(저장소 전수 검색 결과) |
| candle 저장 | NOT_IMPLEMENTED | Parquet/DuckDB/PostgreSQL 등 영속화 코드가 없다. `python/ptengine/backtest/model.py`의 `Candle`은 in-memory value type일 뿐 저장소가 아니다 |
| Python backtest report | NOT_IMPLEMENTED | `python/ptengine/backtest/`에는 `BacktestResult`/metrics 데이터 모델(`model.py`)과 IS/OOS 분리(`evaluation.py`)만 있고, 리포트를 렌더링/생성하는 별도 모듈은 없다 |
| Java paper runtime | NOT_IMPLEMENTED | Scheduling, 지속 실행 loop, 장시간 구동 서비스 코드가 없다. 관련 클래스들의 Javadoc이 "no runtime loop, no scheduling"을 반복해서 명시한다 |
| order intent → risk → OMS → paper fill | IMPLEMENTED_BASELINE (narrow) | Candidate 8, `com.ptengine.integration.PaperOrderPipeline` — 실제 production 코드와 테스트가 존재하지만, 이를 구동하는 runtime loop/scheduler는 없다(한 번의 in-process 호출 단위로만 증명됨) |
| position snapshot | IMPLEMENTED_BASELINE (narrow) | Candidate 9/10, `PositionSnapshot`/`PaperExecutionPositionProjector` — 1건의 FILLED 실행을 최대 1건의 포지션으로 투영하는 production 코드. Fill 집계/포지션 갱신/flat 표현 없음 |
| reconciliation test | PARTIAL | Candidate 11의 `PaperOrderPositionReconciliationIntegrationTest`는 test-only composition(자체 Javadoc 명시). 기반 알고리즘(`PositionReconciler`, Candidate 9)은 production 코드이나, continuous reconciliation service는 없다 |
| daily report | IMPLEMENTED_BASELINE (narrow) | Candidate 12/14, `DailyPaperTradingReport`/`DailyPaperTradingReportGenerator` — deterministic `toPlainText()` 요약과 exact invariant. Persistence·delivery 없음 |
| Telegram alert | NOT_IMPLEMENTED | 저장소 어디에도 alert renderer/transport 코드가 없다. 알림 채널 자체가 `docs/10_OPEN_QUESTIONS_AND_RISKS.md`에서 여전히 DECISION_REQUIRED다 |

---

## 3B. 전체 체크포인트 (Candidate 15)

- **MVP v0.1 foundation**: "JSON logs"를 제외한 모든 항목이
  IMPLEMENTED_BASELINE. Foundation은 실질적으로 구축되었으나, 위 표의
  caveat 없이 "v0.1 완료"라고 선언하지 않는다.
- **MVP v0.2**: order intent→risk→OMS→paper fill, position snapshot, daily
  report의 production 코드/테스트 기반은 존재(IMPLEMENTED_BASELINE,
  narrow)하지만, BingX 데이터 수집, candle 저장, Python backtest report,
  Java paper runtime, Telegram alert는 NOT_IMPLEMENTED이고 reconciliation
  test는 PARTIAL이다. **MVP v0.2는 진행 중이며 완료가 아니다.**
- **프로젝트 전체**: 완료되지 않았다. `COMPLETE`, `production-ready`,
  `paper-ready`, `live-ready`, `canary-ready` 중 어떤 것도 현재 상태에
  해당하지 않는다.
- **Paper qualification**: 최소 30일/45일 권장 기간, 50회 이상 거래,
  paper score 80점 등 `docs/06_VALIDATION_POLICY.md` §7 기준을 충족하는
  실제 paper 운영 기록은 존재하지 않는다. 위 표의 deterministic 테스트
  통과는 이 기준을 대체하지 않는다.
- **Canary/live readiness**: 어떤 canary/live readiness 주장도 하지
  않는다. Live trading은 여전히 비활성 상태이며 명시적 human approval
  없이는 활성화하지 않는다(`CLAUDE.md`).

---

## 3C. 다음 구현 게이트 (결정하지 않음, Candidate 15)

아래는 다음 단계에서 실제로 막힐 지점을 미리 기록한다. Candidate 15는 이
중 어느 것도 확정하지 않는다.

- **데이터 수집/저장**: 정확한 데이터 경계(어떤 timeframe, 어떤 기간, 어떤
  원천)와 거래소 심볼 매핑(`docs/10_OPEN_QUESTIONS_AND_RISKS.md` 항목 1)
  증거가 먼저 필요하다.
- **운영 paper runtime**: position lifecycle/aggregation 경계(fill 집계,
  close/reduce, flat 표현, 재시작 복구)를 정직하게 먼저 정의해야 한다 —
  현재 baseline은 1-fill-to-1-position 투영만 증명한다.
- **alert transport**: 채널 결정(Telegram/Discord/Email,
  `docs/10_OPEN_QUESTIONS_AND_RISKS.md` 항목 9)이 먼저 필요하다 — 아직
  DECISION_REQUIRED다.
- **live/canary**: 계속 이후 단계이며 human-gated다. 이 Candidate는 그
  게이트를 앞당기지 않는다.

---

## 4. MVP v0.3

목표: BingX live adapter 준비와 canary 전 검증

포함:

- BingX adapter skeleton
- API key permission policy
- order placement dry-run/mock
- leverage/margin/position mode validation
- market/limit order guard
- kill switch
- live flag manual approval
- API 장애 시 retry/backoff policy

아직 실제 live trading은 제한적으로만 허용한다.

---

## 5. MVP v0.4

목표: Canary live

포함:

- 극소액 live trading
- max leverage 2x
- strict loss limit
- market order 제한
- reconciliation 강화
- incident report
- rollback to previous deployment

---

## 6. v0.5

목표: 전략 연구 시스템 강화

포함:

- parameter sweep
- walk-forward validation
- out-of-sample report
- strategy versioning
- experiment registry
- failure case 기록

---

## 7. v0.6

목표: 모델/파라미터 버전 관리

포함:

- model_version
- parameter_version
- data_version
- model artifact storage
- deployment manifest
- manual rollback

---

## 8. v0.7

목표: 반자동 학습

포함:

- scheduled retraining
- automatic backtest
- validation score
- Claude summary
- human approval

---

## 9. v1.0

목표: 제한적 무인 운용

포함:

- 자동학습
- 자동검증
- paper promotion
- canary promotion 후보
- 성능 저하 감지
- 자동 중단
- 제한적 롤백

단, live 승격과 risk limit 완화는 계속 human approval을 기본으로 한다.
