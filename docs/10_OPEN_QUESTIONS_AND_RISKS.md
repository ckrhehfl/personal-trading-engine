# OPEN_QUESTIONS_AND_RISKS.md

# 남은 결정사항과 위험 목록 v3

---

## 1. 즉시 결정해야 하는 것

1. BingX 정확한 상품 코드
   - BTC/USDT USDT-M perpetual의 API symbol 확인 필요

2. Position mode
   - hedge mode 또는 one-way mode

3. Margin mode
   - isolated 우선 추천
   - cross는 MVP 비추천

4. 초기 주문 정책
   - limit-first + guarded market 추천

5. Java/Python 공유 schema
   - v0.1은 JSON Schema 추천
   - Protobuf는 나중 검토

6. Java strategy runtime 범위
   - signal까지 Java에서 할지
   - Python signal을 Java가 소비할지

7. VPS 위치
   - BingX API latency 측정 후 결정

8. 알림 채널
   - Telegram 우선 추천

---

## 2. 나중에 결정해도 되는 것

- MLflow 도입 여부
- ONNX export 여부
- PostgreSQL/TimescaleDB 도입
- Aeron/Chronicle Queue/Disruptor 도입
- Java 25 전환 여부
- 멀티거래소 확장 순서
- 멀티심볼 포트폴리오 엔진
- 자동 live 승격 정책

---

## 3. 가장 큰 위험

### 위험 1: 과설계

기관식 구조를 따라가다가 개인 MVP가 너무 커질 수 있다.

완화:

- 서비스는 Python/Java 2개만
- Kafka/Kubernetes 제외
- Java는 OMS/Risk/Execution에만 사용

### 위험 2: 전략 연구 지연

OMS 설계에 너무 오래 걸리면 수익 전략 연구가 늦어진다.

완화:

- Java OMS는 skeleton부터
- Python 백테스트는 병행
- 단순 전략으로 시스템 검증

### 위험 3: Python/Java 불일치

백테스트 결과와 실거래 동작이 달라질 수 있다.

완화:

- shared schema
- manifest 단일화
- snapshot test
- OrderIntent 비교

### 위험 4: 선물/레버리지 리스크

작은 버그도 청산/큰 손실로 이어질 수 있다.

완화:

- isolated margin
- canary max 2x
- strict loss limit
- kill switch
- reduce-only guard later

### 위험 5: LLM 과신

LLM이 그럴듯한 전략이나 운영 판단을 제안할 수 있다.

완화:

- LLM은 보조 역할
- 모든 제안은 테스트/검증으로 변환
- 실거래 판단 금지

---

## 4. Java 채택 문제점 반영 여부 평가

문서에는 Java 채택 문제점이 반영되어 있다.

반영된 문제점:

- 이중 코드베이스
- schema drift
- backtest/live mismatch
- CI/CD 복잡도
- 운영 복잡도
- Java OMS 과설계 위험
- 개발 속도 저하
- Claude 작업 난이도 증가

반영된 완화책:

- Java 범위 제한
- JSON Schema 우선
- Docker Compose만 사용
- Python/Java 책임 분리
- PR 단위 분리
- OMS state machine test
- schema validation
- human approval gate

아직 더 구체화해야 할 것:

- 실제 Java package 구조
- 실제 Python package 구조
- manifest schema 초안
- OrderState enum 정의
- RiskRejectReason enum 정의
- BingX API mapping table
- paper broker fill model
- reconciliation 알고리즘
