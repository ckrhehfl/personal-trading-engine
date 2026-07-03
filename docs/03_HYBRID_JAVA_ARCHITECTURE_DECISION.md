# ADR-0003: Java OMS / Execution 채택 결정

## 상태

Accepted - 초기 아키텍처 결정 초안

---

## 1. 배경

초기에는 Python-only 구조가 제안되었다. Python은 연구, 백테스트, ML, 데이터 처리, Claude 기반 개발 자동화에 강하다. 그러나 현재 요구사항이 다음처럼 강화되었다.

- BTC/USDT 선물
- 레버리지 포함
- long/short 양방향
- market/limit order 모두 지원
- VPS 24/7 운영
- 장기적으로 자동학습/배포/롤백
- 수년~수십년 유지 가능성
- 업계식 OMS 구조 지향

이 요구사항에서는 실거래 경로를 Python-only로 유지하는 것보다 Java 기반 OMS/Execution 계층을 두는 것이 장기적으로 안전할 수 있다.

---

## 2. 결정

Python + Java Hybrid-lite 구조를 채택한다.

```text
Python = Research / Backtest / ML / Report / Deployment Candidate
Java   = OMS / Risk Gateway / Execution / BingX Adapter / Reconciliation
```

---

## 3. 결정 이유

Java는 다음에 적합하다.

- 주문 상태 머신
- 강한 타입 기반 도메인 모델
- long-running service
- 동시 이벤트 처리
- 체결/포지션 상태 관리
- partial fill 처리
- idempotency
- health check
- structured logging
- 운영 안정성

특히 선물 거래에서는 주문, 포지션, 레버리지, margin mode, position mode, reduce-only, partial fill, API 장애 처리가 중요하다.

---

## 4. 대안

### 대안 A: Python-only

장점:

- 빠른 개발
- 연구/백테스트/ML 통합 쉬움
- Claude Code와 궁합 좋음
- 1인 개발 복잡도 낮음

단점:

- OMS 상태 관리가 복잡해질수록 위험
- live path와 research path가 섞일 가능성
- 장기 운영 안정성 우려
- 타입/상태 머신이 약하게 흩어질 수 있음

### 대안 B: Java-only

장점:

- live path 안정성
- 타입 안정성
- OMS 구현 용이

단점:

- 연구/백테스트/ML 속도 저하
- Python quant 생태계 활용 어려움
- 1인 개발 생산성 저하

### 대안 C: Python + Java Hybrid-lite

장점:

- 연구는 Python으로 빠르게 수행
- 실거래 경로는 Java로 안정화
- 장기 리팩토링 비용 감소
- 업계식 구조와 유사

단점:

- 이중 코드베이스
- 스키마 관리 필요
- 백테스트/live 불일치 위험
- 배포/테스트 복잡도 증가

결론: C를 채택한다.

---

## 5. Java 사용 범위 제한

Java는 다음에만 사용한다.

- OMS
- Risk Gateway
- Execution Service
- Exchange Adapter
- Position Reconciliation
- Kill Switch
- Paper/Live Trading Runtime

Java는 초기에는 다음을 하지 않는다.

- 연구 notebook
- 대규모 백테스트
- ML training
- 전략 실험 자동화
- 리포트 생성

---

## 6. 주요 위험과 완화책

### 위험 1: 이중 코드베이스 복잡도

완화:

- 서비스는 처음에 2개만 유지
- 공유 schema를 먼저 정의
- Python/Java 책임을 문서화
- 전략 로직 중복 금지

### 위험 2: 백테스트와 live 불일치

완화:

- OrderIntent schema 고정
- 동일 데이터로 signal snapshot 비교
- Java Risk Gateway를 backtest validation에도 적용
- deployment manifest를 단일 source of truth로 사용

### 위험 3: Java OMS 과설계

완화:

- 초기에는 Spring/Kafka/Aeron/Chronicle 미사용
- Java 21 + Gradle + JUnit + Jackson + SLF4J 정도로 시작
- paper broker부터 구현
- BingX live adapter는 나중에 연결

### 위험 4: 개발 속도 저하

완화:

- 전략 연구는 Python 유지
- Java는 작은 상태 머신 중심으로 구현
- Claude Code subagent를 Java OMS reviewer로 분리

### 위험 5: 운영 복잡도 증가

완화:

- Docker Compose만 사용
- Kubernetes 제외
- 단일 VPS 기준
- 로그/헬스체크/알림 최소 구성

---

## 7. 재평가 기준

다음 상황이면 Java OMS 채택을 재검토한다.

- 1인 개발 부담이 과도하게 증가
- Java OMS 개발로 인해 백테스트/전략 연구가 장기간 멈춤
- Python prototype만으로 충분한 안정성이 측정됨
- Java/Python schema drift가 반복 발생

다음 상황이면 Java OMS 채택을 강화한다.

- paper trading에서 주문 상태/체결/포지션 관리 이슈가 발생
- Python async live path가 복잡해짐
- 실거래 운영 시간이 길어짐
- 여러 심볼/거래소 확장이 필요해짐
