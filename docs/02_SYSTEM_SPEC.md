# SYSTEM_SPEC.md

# 시스템 스펙 v3: Python + Java Hybrid-lite

---

## 1. 시스템 목표

시스템은 연구/백테스트/자동학습과 실거래 실행을 명확히 분리한다.

```text
Research Plane = Python
Trading Plane = Java
```

이 분리는 단순 기술 취향이 아니라 돈이 걸린 live path를 안정적으로 운영하기 위한 구조적 결정이다.

---

## 2. 전체 아키텍처

```text
[Python Research Plane]
  Data Research
  Backtest
  Strategy Experiments
  ML Training
  Validation Report
  Deployment Candidate Manifest

          ↓ manifest / config / model artifact

[Java Trading Plane]
  Config Loader
  Strategy Runtime or Signal Loader
  Risk Gateway
  OMS
  Execution Service
  BingX Adapter
  Position Reconciliation
  Kill Switch
  Paper/Live Runtime

          ↓ logs / fills / state / metrics

[Storage & Ops]
  Parquet / DuckDB / PostgreSQL
  JSON logs
  Reports
  Telegram Alerts
```

---

## 3. Python Research Plane

Python은 다음을 담당한다.

- historical data 수집/정리
- 데이터 품질 검사
- 백테스트
- 전략 연구
- parameter sweep
- ML/자동학습
- 모델 export
- 리포트 생성
- 배포 후보 manifest 생성

Python이 직접 live order를 보내지 않는다.

---

## 4. Java Trading Plane

Java는 다음을 담당한다.

- 주문 상태 머신
- order intent 검증
- risk check
- order routing
- BingX API 호출
- WebSocket 이벤트 처리
- partial fill 처리
- 중복 주문 방지
- position reconciliation
- leverage/margin/position mode 검증
- market/limit order guard
- kill switch
- live/paper runtime

Java가 최종 방어선이다.

---

## 5. 핵심 도메인 모델

Java 쪽 핵심 모델:

```text
OrderIntent
RiskDecision
RiskRejectReason
AcceptedOrder
ExchangeOrder
OrderState
FillEvent
PositionSnapshot
AccountSnapshot
ReconciliationResult
DeploymentManifest
KillSwitchState
```

Python 쪽 핵심 모델:

```text
StrategySpec
BacktestRun
ExperimentRun
FeatureSpec
ModelArtifact
ParameterSet
ValidationReport
DeploymentCandidate
```

---

## 6. 공유 계약

Python과 Java는 같은 도메인 계약을 공유해야 한다.

초기 후보:

```text
Option A: JSON Schema + pydantic + Jackson
Option B: Protobuf
```

초기 추천:

```text
v0.1: JSON Schema
v0.4 이후: 필요하면 Protobuf 검토
```

이유:

- JSON/YAML manifest는 사람이 읽기 쉽다.
- Claude Code가 다루기 쉽다.
- 초기 개발 속도가 빠르다.
- 단, schema validation은 반드시 필요하다.

---

## 7. 백테스트와 Live 일치성 문제

Hybrid 구조의 가장 큰 위험은 Python 백테스트와 Java live runtime의 불일치다.

방지책:

1. 전략의 입력/출력 schema 고정
2. OrderIntent 단위로 백테스트/live 결과 비교
3. 동일 candle에서 Python signal과 Java signal snapshot 비교
4. Java Risk Gateway 테스트를 Python backtest 결과에도 적용
5. deployment manifest를 단일 source of truth로 사용
6. 전략 로직을 중복 구현하지 않기

---

## 8. 전략 실행 방식 선택지

### 방법 A: Python Signal Generator + Java Execution

Python이 signal/target position을 생성하고 Java가 risk/execution을 담당한다.

장점:

- 연구 속도가 빠름
- 백테스트와 signal이 일치하기 쉬움

단점:

- live 중 Python process도 안정적으로 운영해야 함

### 방법 B: Java Strategy Runtime

Python은 파라미터/모델만 생성하고 Java가 signal도 생성한다.

장점:

- live path가 단순하고 안정적

단점:

- 전략 중복 구현 위험
- 연구 속도 저하

### 초기 추천

```text
v0.1~v0.2:
Java에는 단순 rule strategy runtime만 둔다.
Python은 연구/백테스트를 담당한다.

v0.3 이후:
검증된 전략만 Java runtime으로 옮기거나,
Python-generated signal을 Java가 소비하는 방식을 유지한다.

ML 모델은 장기적으로 ONNX export 후 Java inference를 검토한다.
```

---

## 9. 데이터 저장소

초기:

- Parquet
- DuckDB
- JSON logs
- YAML configs

중기:

- PostgreSQL
- TimescaleDB optional
- object storage optional

원칙:

- raw data와 processed data 분리
- data_version 기록
- fetched_at 기록
- checksum 기록
- timezone은 UTC

---

## 10. 배포 구조

초기 배포는 Docker Compose를 사용한다.

```yaml
services:
  trading-engine-java:
    role: OMS/Risk/Execution/Paper-Live Runtime

  researcher-python:
    role: Backtest/Research/Report/Training

  storage:
    role: DuckDB/Parquet/PostgreSQL

  alert:
    role: Telegram or webhook
```

Kubernetes는 제외한다.

---

## 11. 기술스택 초안

### Python

- Python 3.12+
- uv 또는 Poetry
- pydantic
- pandas / polars
- numpy
- duckdb
- pyarrow
- pytest
- ruff
- mypy 또는 pyright
- scikit-learn / xgboost / lightgbm later
- MLflow later

### Java

- Java 21 LTS 우선
- Java 25 LTS는 ecosystem/운영 안정성 확인 후 검토
- Gradle
- Jackson
- SLF4J + Logback
- JUnit 5
- Resilience4j
- OkHttp 또는 Java HttpClient
- WebSocket client
- Testcontainers optional

### 나중에 검토

- Chronicle Queue
- Aeron
- Agrona
- LMAX Disruptor
- Netty
- Kafka

초기에는 위 고급 도구를 쓰지 않는다.
