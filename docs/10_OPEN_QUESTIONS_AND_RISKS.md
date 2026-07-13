# OPEN_QUESTIONS_AND_RISKS.md

# 남은 결정사항과 위험 목록 v4

## 1. 미확정 항목

현재 미확정 항목은 정확히 8개다(기존 cross-reference 안정성을 위해 item identifier
2..9를 유지한다). 해결된 옛 항목 1은 §1A를 참고한다. 현재 작업을 막지 않는 항목은
필요 시점까지 defer한다.

2. Position mode
   - hedge mode 또는 one-way mode
3. Margin mode
   - isolated 우선 검토
4. 초기 주문 정책 세부
   - limit-first + guarded market 방향은 유지하되 세부 미확정
5. 손절/익절 방식
   - 거래소 native stop 또는 내부 risk stop
6. Java strategy runtime 범위
   - signal까지 Java에서 할지, Python signal을 Java가 소비할지
7. 백테스트 ↔ Java trading path 일치성 테스트 방법
8. VPS 위치
   - BingX API latency 측정 후 결정
9. 알림 채널
   - Telegram / Discord / Email

공유 계약 형식은 D011에서 해결되었다. MVP v0.1은 JSON Schema Draft 2020-12를 사용한다.

### 1A. 해결된 항목

1. BingX 정확한 상품 코드
   - BTC/USDT USDT-M perpetual의 public read-only BingX Swap V2 recent-trades
     endpoint(`GET /openApi/swap/v2/quote/trades`) symbol은 `BTC-USDT`다.
   - 근거: Candidate 18 / Issue #42 / PR #43,
     `com.ptengine.bingx.market.BingxPublicMarketDataClient`의 실측 검증, 결정
     기록은 `docs/11_DECISION_LOG.md` D013.
   - 해결 범위: public unauthenticated recent-trades read symbol mapping only.
   - 미해결/미구현: 다른 symbol/product 매핑, ticker/order-book mapping,
     private/account/position/order mapping, order-write 권한,
     market-data storage/runtime/WebSocket.

1B. BingX BTC-USDT 15m kline interval token 및 응답 매핑
   - 제품 소스오브트루스가 이미 확정한 15m 기본 타임프레임에 대해, public
     read-only BingX Swap V3 kline endpoint(`GET /openApi/swap/v3/quote/klines`)
     interval token은 `15m`이고 symbol은 `BTC-USDT`(항목 1과 동일)다.
   - 근거: Candidate 19 / Issue #44,
     `com.ptengine.bingx.market.BingxPublicMarketDataClient#fetchRecentBtcUsdt15mCandles`의
     실측 검증, 결정 기록은 `docs/11_DECISION_LOG.md` D014.
   - 해결 범위: public unauthenticated 15m kline read symbol/interval/response
     mapping only. `limit`은 1~1000에서만 정확히 지켜지며(1000 초과 요청 시
     반환 건수는 실제로 1000으로 capped), 응답 배열 순서(newest-first)는
     관측되었을 뿐 계약으로 보장되지 않는다. 선두 원소가 아직 마감되지 않은
     candle일 수 있다.
   - 미해결/미구현: `startTime`/`endTime` 기반 historical range, 다른
     symbol/product 매핑, 다른 timeframe, candle 저장, collector/scheduler,
     WebSocket, private/account/order mapping.

BingX API mapping table은 `docs/04_MVP_SCOPE_AND_ROADMAP.md` §3A/§4.1 기준으로
여전히 PARTIAL이다. 전체 BingX API 모드가 해결됐다고 간주하지 않는다.

## 2. 나중에 결정해도 되는 것

- MLflow 도입 여부
- ONNX export 여부
- PostgreSQL/TimescaleDB 도입
- Aeron/Chronicle Queue/Disruptor 도입
- Java 25 전환 여부
- 멀티거래소 확장 순서
- 멀티심볼 포트폴리오 엔진
- 자동 승격 정책

## 3. 가장 큰 위험

### 위험 1: 과설계

완화:

- 서비스는 Python/Java 두 plane으로 제한
- Kafka/Kubernetes 제외
- Java는 OMS/Risk/Execution 중심

### 위험 2: 전략 연구 지연

완화:

- Java OMS는 skeleton부터
- Python 백테스트는 별도 작은 PR로 진행
- 단순 전략으로 시스템 검증

### 위험 3: Python/Java 불일치

완화:

- shared schema
- manifest 단일화
- snapshot test
- OrderIntent 비교

**Candidate 15 재확인**: shared-schema JSON-boundary compatibility
(Candidate 5, `ContractJsonCodec` + `SharedFixtureCompatibilityTest`)와
1건의 aligned-input MARKET-fill 동등성 baseline(Candidate 13,
`tests/execution/fixtures/python-java-market-fill-equivalence-v0.1.json`)은
지금 존재한다. 더 넓은 parity는 여전히 열려 있다 — 명시적으로 남은 gap:

- LIMIT / NO_FILL 동등성
- 일반 슬리피지 모델 parity
- signal snapshot parity
- risk decision/rule parity
- runtime parity

### 위험 4: 선물/레버리지 위험

완화:

- 초기 안전 설정 우선
- strict loss limit
- kill switch
- reduce-only guard 후속 구현

### 위험 5: LLM 과신

완화:

- LLM은 보조 역할
- 모든 제안은 테스트/검증으로 변환
- 직접 매매 판단 금지

## 4. Java 채택 문제점 반영 여부

반영된 문제점:

- 이중 코드베이스
- schema drift
- backtest/runtime mismatch
- CI/CD 복잡도
- 운영 복잡도
- Java OMS 과설계 위험
- 개발 속도 저하
- Claude 작업 난이도 증가

반영된 완화책:

- Java 범위 제한
- JSON Schema Draft 2020-12 사용(D011)
- Docker Compose만 사용
- Python/Java 책임 분리
- PR 단위 분리
- OMS state machine test
- schema validation
- human approval gate

### 4.1 기술 항목 상태 재확인 (Candidate 19)

Candidate 1–19를 거치며 아래 항목들은 IMPLEMENTED_BASELINE과 PARTIAL이 섞인
상태를 갖게 되었다(예: BingX API mapping table은 PARTIAL, 그 외 항목 다수는
IMPLEMENTED_BASELINE). Production-complete를 의미하지 않으며, 정확한 경계는
`docs/04_MVP_SCOPE_AND_ROADMAP.md`가 기준이다.

- **실제 Java package 구조** — IMPLEMENTED_BASELINE:
  `java/src/main/java/com/ptengine/{contract,oms,risk,paper,integration,reconciliation,report}`.
- **실제 Python package 구조** — IMPLEMENTED_BASELINE:
  `python/ptengine/backtest/{model,engine,strategy,evaluation}.py`.
- **`OrderState` enum 정의** — IMPLEMENTED_BASELINE:
  `com.ptengine.oms.OrderState`(Candidate 2). 단일 프로세스 in-memory
  skeleton 경계 안에서만.
- **`RiskRejectReason` enum 정의** — IMPLEMENTED_BASELINE:
  `com.ptengine.risk.RiskRejectReason`(Candidate 3), 3개 값
  (`RISK_CONFIGURATION_MISSING`, `RISK_ENGINE_ERROR`,
  `RISK_STATE_DEGRADED` 예약). Production numeric risk rule에 대응하는
  reason은 아직 없음.
- **paper broker fill model** — IMPLEMENTED_BASELINE:
  `com.ptengine.paper.PaperBroker`(Candidate 6). MARKET/LIMIT
  fill-or-no-fill 로직만, partial fill/슬리피지 모델 없음.
- **reconciliation 알고리즘** — IMPLEMENTED_BASELINE:
  `com.ptengine.reconciliation.PositionReconciler`(Candidate 9). 1건 대
  1건 field-for-field 비교만, continuous service 아님.

여전히 미확정/미구현인 항목:

- **manifest generation/consumption path** — NOT_IMPLEMENTED.
  `schemas/v0.1/deployment-manifest.schema.json`(Candidate 7)은 shape
  validation만 제공하며, 어떤 runtime도 이 manifest를 생성하거나 소비하지
  않는다.
- **BingX API mapping table** — PARTIAL (Candidate 19). 현재 매핑은 정확히
  둘이다: (1) product `BTC/USDT USDT-M perpetual` → API symbol `BTC-USDT` →
  endpoint `/openApi/swap/v2/quote/trades` → use: public recent-trades batch
  read (`com.ptengine.bingx.market.BingxPublicMarketDataClient#fetchRecentBtcUsdtTrades`,
  Candidate 18); (2) 동일 product/symbol → endpoint
  `/openApi/swap/v3/quote/klines`(interval `15m`) → use: public 15m kline
  batch read
  (`com.ptengine.bingx.market.BingxPublicMarketDataClient#fetchRecentBtcUsdt15mCandles`,
  Candidate 19). 두 endpoint 모두 ordering semantics(어느 원소가 최신인지)는
  계약으로 확립되지 않았고, `limit` query는 count guarantee로 신뢰하지
  않는다. 다른 symbol, historical range query, candle 저장, market-data
  storage, WebSocket, ticker/order-book mapping, private/account endpoint,
  order mapping은 여전히 미구현/미결정이다.

### 4.2 새로 확인된 미해결 경계 (Candidate 15)

Candidate 1–14 구현을 감사한 결과 다음 경계가 아직 열려 있음을 확인했다.
이들은 새 product 결정이 아니라 구현 사실이다.

- **Fill 집계 / partial-fill 회계** — `PaperExecutionPositionProjector`는
  1건의 FILLED 실행만 1건의 포지션으로 투영하며, 여러 fill을 합산하지
  않는다.
- **Close/reduce 및 flat-position 표현** — `PositionSnapshot`은 정확히
  하나의 열린 포지션만 모델링하며, flat/no-position이나 포지션
  축소·청산을 표현하지 않는다.
- **영속적/재시작 복구 가능한 상태** — `OrderRegistry`, `PositionSnapshot`
  모두 in-memory이며 재시작 복구 경로가 없다.
- **Runtime scheduling/orchestration** — 어떤 구성요소도 장시간 구동
  loop나 스케줄러에 연결되어 있지 않다.
- **데이터 영속화 포맷/구현** — candle이나 실행 기록을 저장하는
  Parquet/DuckDB/PostgreSQL 등 구현이 없다.
- **Alert transport** — 알림을 실제로 전송하는 코드가 없다(채널 자체도
  §1 항목 9에서 여전히 미확정).
