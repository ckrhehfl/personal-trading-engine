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
