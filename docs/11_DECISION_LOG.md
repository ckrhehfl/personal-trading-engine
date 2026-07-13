# DECISION_LOG.md

# 의사결정 로그 v4

---

## D001: 초기 거래소

BingX를 초기 거래소로 선택한다.

이유:

- 사용자가 데모와 운영 검증에 BingX를 사용할 계획
- BTC/USDT 선물 시작 가능

---

## D002: 초기 상품

BTC/USDT USDT-M Perpetual Futures를 초기 상품으로 한다.

---

## D003: 방향성

Long/Short 모두 허용한다.

단, risk engine과 position mode 검증을 필수로 한다.

---

## D004: 레버리지

레버리지는 지원한다.

하지만 canary 단계에서는 최대 2x, stable 단계에서도 최대 3x를 기본 hard limit으로 한다.

---

## D005: 주문 방식

Market/Limit 모두 지원한다.

하지만 기본 정책은 limit-first, market은 guard 조건에서만 허용한다.

---

## D006: 운영 환경

VPS 우선으로 한다.

로컬 노트북은 개발, 백테스트, paper 보조용으로 사용한다.

---

## D007: 기술스택

Python + Java Hybrid-lite를 채택한다.

Python:

- Research
- Backtest
- ML
- Report
- Deployment candidate

Java:

- OMS
- Risk Gateway
- Execution
- BingX Adapter
- Reconciliation

---

## D008: 트레이딩 프레임워크

메인 엔진은 custom hybrid core로 한다.

- Freqtrade: 구조 참고 또는 빠른 비교용
- LEAN: 기관식 구조 참고용
- CCXT: market data / API adapter 검증용
- BingX native API: 최종 adapter 기준

---

## D009: 자동학습

자동학습은 장기 목표로 포함하되 MVP 구현에서는 제외한다.

초기에는 버전 관리와 manifest 구조만 만든다.

---

## D010: LLM

LLM은 실시간 매매 판단자가 아니다.

연구, 운영, 리스크, 장애 분석 보조로 사용한다.

---

## D011: Python↔Java 공유 계약 형식

MVP v0.1의 Python↔Java 공유 계약은 JSON Schema Draft 2020-12를 사용한다.

- canonical contract는 `schemas/v0.1/*.schema.json`이다.
- versioning과 validation 규칙은 `schemas/README.md`가 보조한다.
- Protobuf는 영구 배제하지 않지만 현재 도입하지 않는다.
- 성능, interoperability, schema evolution, code generation에서 측정 가능한 필요가 생길 때만 재검토한다.

---

## D012: DeploymentManifest v0.1 schema baseline

canonical v0.1 `DeploymentManifest`는 `schemas/v0.1/deployment-manifest.schema.json`에
정의된 candidate-only JSON Schema 계약이다.

- `status`는 이 baseline에서 `CANDIDATE`만 허용한다.
- schema validation은 deployment/risk/live authorization이 아니다 — shape
  validation일 뿐이며, 참조된 backtest/artifact/risk profile의 존재나
  유효성을 증명하지 않는다.
- 이 baseline에는 production risk 수치 값(leverage, notional, exposure,
  loss limit 등)이나 live flag가 없다.
- Python manifest generator, Java manifest loader/parser, cross-language
  compatibility test는 이 decision의 범위 밖이며 이후 Candidate에서 다룬다.

---

## D013: BingX BTC-USDT public market-data symbol mapping

초기 상품 BTC/USDT USDT-M perpetual의 BingX Swap V2 public unauthenticated
recent-trades REST read symbol은 `BTC-USDT`다.

- endpoint: `GET /openApi/swap/v2/quote/trades`
- use: read-only recent-trades batch read 경계
  (`com.ptengine.bingx.market.BingxPublicMarketDataClient`)
- Candidate 18 / Issue #42 / PR #43에서 검증/구현되었다.

제한:

- private/account/position/order API mapping은 이 decision의 범위 밖이며
  미결정이다.
- order-write 권한을 부여하지 않는다.
- candle/kline timeframe, ticker/order-book mapping은 미결정이다.
- `limit` query에 대한 count guarantee나 배열 ordering semantics를 확정하지
  않는다.
- runtime/persistence/WebSocket/live trading authority와 무관하다.

---

## D014: BingX BTC-USDT 15m kline public market-data mapping

제품 소스오브트루스(`docs/00_MASTER_SUMMARY.md` §2)가 이미 확정한 초기
타임프레임 15m에 대해, BingX Swap V3 public unauthenticated kline REST read
엔드포인트와 interval token을 잠근다.

- endpoint: `GET /openApi/swap/v3/quote/klines`
- symbol: `BTC-USDT` (D013과 동일 매핑)
- interval token: `15m`
- use: read-only one-shot kline batch read 경계
  (`com.ptengine.bingx.market.BingxPublicMarketDataClient#fetchRecentBtcUsdt15mCandles`)
- Candidate 19 / Issue #44에서 검증/구현되었다.

근거(fresh live read-only 관측, 2026-07-13):

- 공식 대화형 문서(`bingx-api.github.io/docs`)는 JS-rendered SPA로 정적
  조회가 불가능했다. 이 decision은 fresh live GET 관측(다양한 `limit` 값에
  대해 20회 이상)과 CCXT(`ccxt/ccxt` `bingx.py` `parseOHLCV`) 2차 corroboration에
  근거한다.
- 응답 wire shape은 candle당 정확히 6개 키(`open`,`high`,`low`,`close`,
  `volume`,`time`)를 가진 JSON **object**다. `BingX-API/api-ai-skills` 저장소의
  `swap-market/api-reference.md`는 11-요소 positional array(및 `closeTime`,
  quote volume, trade count, taker-buy volume 필드)를 문서화하지만, 이는
  live 관측 및 CCXT 파싱 로직과 모두 불일치하여 이 decision에서는 채택하지
  않는다 — 이 불일치는 Issue #44에 투명하게 기록되어 있다.
- `limit`은 요청값 1~1000에서는 정확히 지켜지나(`limit=999`→999건 등),
  validation은 1440까지 허용하면서도 1000 초과 요청 시 실제 반환 건수는
  1000으로 silently capped된다(`limit=1440`→1000건). 이 client는 항상
  `limit=1000`을 요청하고, 응답 배치 크기를 1~1000 범위로 별도 검증한다.
- 응답의 선두 원소가 아직 마감되지 않은(forming) candle을 포함할 수 있음을
  live로 확인했다(동일 `time`에 대해 연속 호출 간 `close` 값이 변경됨).
  `isClosed`에 해당하는 wire 필드는 없다.

제한:

- private/account/position/order API mapping은 이 decision의 범위 밖이며
  여전히 미결정이다.
- order-write 권한을 부여하지 않는다.
- `startTime`/`endTime` 기반 historical range query, 다른 symbol/product
  매핑, ticker/order-book mapping은 미결정/미구현이다.
- 배열 순서(newest-first)는 매 관측에서 일관됐으나 계약으로 보장되지
  않는다 — wire order만 보존하며 latest/oldest 의미를 assert하지 않는다.
- runtime/persistence/scheduler/WebSocket/live trading authority와
  무관하다.
