# OPEN_QUESTIONS_AND_RISKS.md

# 남은 결정사항과 위험 목록 v4

## 1. 미확정 항목

현재 미확정 항목은 다음 9개다. 현재 작업을 막지 않는 항목은 필요 시점까지 defer한다.

1. BingX 정확한 상품 코드
   - BTC/USDT USDT-M perpetual의 API symbol 확인 필요
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

아직 더 구체화해야 할 것:

- 실제 Java package 구조
- 실제 Python package 구조
- manifest schema 초안
- OrderState enum 정의
- RiskRejectReason enum 정의
- BingX API mapping table
- paper broker fill model
- reconciliation 알고리즘
