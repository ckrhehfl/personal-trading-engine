# DECISION_LOG.md

# 의사결정 로그 v4

---

## D001: 초기 거래소

BingX를 초기 거래소로 선택한다.

---

## D002: 초기 상품

BTC/USDT USDT-M Perpetual Futures를 초기 상품으로 한다.

---

## D003: 방향성

Long/Short 모두 허용한다. 단, risk engine과 position mode 검증을 필수로 한다.

---

## D004: 레버리지

레버리지는 지원한다. canary 단계 최대 2x, stable 단계 최대 3x를 기본 hard limit으로 한다.

---

## D005: 주문 방식

Market/Limit 모두 지원한다. 기본 정책은 limit-first이며 market은 guard 조건에서만 허용한다.

---

## D006: 운영 환경

VPS 우선으로 한다. 로컬 노트북은 개발, 백테스트, paper 보조용으로 사용한다.

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

자동학습은 장기 목표로 포함하되 MVP 구현에서는 제외한다. 초기에는 버전 관리와 manifest 구조만 만든다.

---

## D010: LLM

LLM은 실시간 매매 판단자가 아니다. 연구, 운영, 리스크, 장애 분석 보조로 사용한다.

---

## D011: Python↔Java 공유 계약 형식

MVP v0.1의 Python↔Java 공유 계약은 JSON Schema Draft 2020-12를 사용한다.

- canonical contract는 `schemas/v0.1/*.schema.json`이다.
- versioning과 validation 규칙은 `schemas/README.md`가 보조한다.
- Protobuf는 영구 배제하지 않지만 현재 도입하지 않는다.
- 성능, interoperability, schema evolution, code generation에서 측정 가능한 필요가 생길 때만 재검토한다.
