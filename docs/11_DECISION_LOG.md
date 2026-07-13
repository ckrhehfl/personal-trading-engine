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

---

## D015: BingX BTC-USDT 15m kline bounded historical range query semantics

D014이 잠근 15m kline read symbol/interval/응답 매핑 위에서, 동일
엔드포인트(`GET /openApi/swap/v3/quote/klines`)의 `startTime`/`endTime` query
parameter 정확한 semantics와, 이를 안전하게 1회 GET으로 소비하기 위한
client-side 범위 제약을 잠근다.

- endpoint: `GET /openApi/swap/v3/quote/klines` (D013/D014과 동일)
- symbol: `BTC-USDT`, interval: `15m` (변경 없음)
- 신규 query parameter: `startTime`, `endTime` (둘 다 epoch millisecond,
  camelCase)
- use: read-only one-shot bounded historical kline range read 경계
  (`com.ptengine.bingx.market.BingxPublicMarketDataClient#fetchBtcUsdt15mCandlesInRange`)
- Candidate 20 / Issue #46에서 검증/구현되었다.

근거(fresh live read-only 관측, 2026-07-13, `https://open-api.bingx.com`
대상 약 25회 unauthenticated GET; 공식 대화형 문서는 D014과 동일하게
JS-rendered SPA로 정적 조회 불가 재확인; CCXT `ccxt/ccxt` `bingx.py`
`fetch_ohlcv`는 2차 corroboration으로만 사용 — 동일 parameter 이름을
확인시켜 주나 자체적으로 방어적인 `startTime = since - 1` 보정을 적용하며,
이는 이 decision이 채택하는 실측 inclusivity와 다르다. D014 선례와 동일하게
CCXT는 secondary일 뿐이며 이 차이를 "docs drift"로 취급하지 않는다):

- `startTime`과 `endTime`을 함께 보낼 때(이 client가 항상 사용하는 모드),
  candle open time(`time` 필드) 기준 half-open interval
  `startTime <= time < endTime`이 일관되게 적용된다 — `startTime`은
  inclusive, `endTime`은 exclusive다. 8회 이상 독립적인 실측으로 경계값
  (bucket 시작/끝, next-bucket open)까지 모순 없이 확인했다.
- `limit`은 range filter와 별개의 추가 상한으로 작동한다. Range가 함의하는
  candle 수가 `limit` 또는 서버 자체 1000건 상한을 넘으면, 응답은
  `code=0`(에러 아님)을 유지한 채 **가장 최근(newest) 쪽을 남기고 가장
  오래된(oldest) 쪽을 조용히 잘라낸다**. 1001-candle 범위에서 `limit=1000`
  요청 시 정확히 1000건이 newest-anchored로 반환되어 가장 오래된 1건만
  사라짐을 확인했고, 동일한 5-candle 범위를 `limit=2` vs `limit=1000`으로
  요청해 동일한 방향의 절단을 재확인했다.
- 서버 자체 수치 검증: `startTime`은 0 이상이어야 하며 위반 시
  `code=109400`(`msg`가 명시적으로 위반 필드를 지목); `endTime`은
  `17514115200000` 이하여야 하며 위반 시 동일하게 `code=109400`.
- `startTime == endTime`(정렬/비정렬 모두)은 정당한 성공으로 빈 배열을
  반환한다(`code=0`, `data=[]`). `startTime > endTime`은 명시적 에러
  `code=109400`, `msg="startTime is later than endTime."`.
- 미래 range와, 시장 데이터가 존재하기 이전(2015-01-01 UTC로 실측)의 과거
  range 둘 다 `code=0`, `data=[]`인 정당한 빈 성공으로 확인됐다.
- Range가 "지금"을 포함하면 아직 마감되지 않은(forming) candle이 포함될 수
  있다 — D014과 동일하게 `isClosed`에 해당하는 wire 필드는 없다.
- 비정렬(non-15m-grid) `startTime`/`endTime`도 서버는 그대로 받아들이며,
  candle open time과의 단순 수치 비교로 필터링된다(bucket snapping 없음).

Client-side 안전 범위 계약(거래소 자체보다 의도적으로 좁음):
`fetchBtcUsdt15mCandlesInRange(long startTimeEpochMs, long endTimeEpochMs)`는
두 인자 모두 0 이상이고 900,000ms(15분) grid에 정렬되어 있으며,
`endTimeEpochMs`가 `startTimeEpochMs`보다 엄격히 크고, 그 차이가
900,000,000ms(1000 candle) 이하일 것을 요구한다. 이 조건을 만족하지 않으면
transport 호출 전에 fail-closed로 거부한다. 900,000,000ms 폭에서 정확히
1000건이 절단 없이 반환됨을 실측했고, 그보다 1 candle 더 넓은 범위에서는
가장 오래된 1건이 조용히 사라짐을 실측했다 — 두 bound 모두 grid-정렬을
요구하는 이유는, 비정렬 입력의 동일 폭이 grid 경계를 걸쳐 최대 1001개의
candle open을 함의할 수 있어 "무절단" 보장이 깨질 수 있기 때문이다. 이
차이 때문에 이 client는 거래소가 실제로 허용하는 범위보다 의도적으로 좁다.

빈 결과 정책: 유효하지만 결과가 없는(미래/과거-no-data) range는 이 신규
range 메서드에서만 정당한 빈 배치로 허용한다. 기존
`fetchRecentBtcUsdt15mCandles`/`fetchRecentBtcUsdtTrades`는 빈 `data`를
그대로 거부하며 이 decision으로 완화되지 않는다. `startTime == endTime`,
`startTime > endTime`은 거래소가 어떻게 응답하든 이 client가 인자 검증
단계에서 먼저 거부한다(transport 미호출).

제한:

- private/account/position/order API mapping은 이 decision의 범위 밖이며
  여전히 미결정이다.
- order-write 권한을 부여하지 않는다.
- Pagination, collector, historical backfill, storage, retention, gap
  repair, scheduler, WebSocket, 다른 symbol/timeframe 매핑은 여전히
  미결정/미구현이다 — 이 decision은 정확히 한 번의 bounded GET 경계만
  잠근다.
- 배열 순서(newest-first)는 매 관측에서 일관됐으나 계약으로 보장되지
  않는다 — wire order만 보존한다.
- runtime/persistence/scheduler/WebSocket/live trading authority와
  무관하다.
