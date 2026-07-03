# Trading Bot Spec v3 - All In One

# 개인용 기관식 BTC/USDT 선물 자동매매 시스템 v3 중간정리

> 상태: 초기 요구사항/아키텍처 초안  
> 핵심 변경: Python-only에서 **Python Research + Java OMS/Execution Hybrid-lite**로 변경  
> 목표: 개인용이지만 업계식 원칙을 따르는 장기 운용 가능한 자동매매 시스템

---

## 1. 한 문장 정의

BingX의 BTC/USDT 선물 시장에서 시작하여, 장기적으로 여러 거래소·여러 심볼·다른 자산군으로 확장 가능한 개인용 자동매매 시스템을 만든다. 전략 수익성보다 먼저 데이터, 백테스트, 리스크, OMS, 실행, 운영, 검증, 배포/롤백 구조를 완성한다.

---

## 2. 현재 확정된 요구사항

| 항목 | 결정 |
|---|---|
| 초기 거래소 | BingX |
| 초기 상품 | BTC/USDT USDT-M Perpetual Futures |
| 방향 | Long / Short 모두 허용 |
| 레버리지 | 포함하되 단계별 제한 |
| 주문 방식 | Market / Limit 둘 다 지원 |
| 초기 타임프레임 | 15m 기본, 5m 확장, 1h regime filter |
| 운영 환경 | VPS 우선, 로컬은 개발/백테스트/paper 보조 |
| 서비스 형태 | SaaS 아님, 개인용 단일 사용자 |
| 전략 | 초기에는 단순 테스트 전략, 이후 연구 시스템으로 확장 |
| HFT | 제외 |
| 내부 처리 지연 목표 | p95 100ms 이하를 목표로 측정/관리 |
| 자동학습/배포/롤백 | 장기 목표로 문서화, MVP 구현 제외 |
| LLM 사용 | 실시간 매매 판단자가 아니라 연구/운영/리스크/장애 분석 보조 |
| 기술스택 | Python + Java Hybrid-lite |

---

## 3. 핵심 아키텍처 결정

기존 Python-only 방식을 수정하여 다음 구조를 채택한다.

```text
Python Research Plane
- 데이터 연구
- 백테스트
- 전략 실험
- ML/자동학습
- 리포트
- 배포 후보 생성

Java Trading Plane
- OMS
- Risk Gateway
- Execution Service
- BingX Adapter
- Position Reconciliation
- Kill Switch
- Paper/Live Trading Runtime
```

핵심 원칙은 다음이다.

```text
Python은 연구하고 후보를 만든다.
Java는 돈이 걸린 실시간 경로를 통제한다.
```

---

## 4. 왜 Java를 채택하는가

Java는 다음 영역에서 강점을 가진다.

- 주문 상태 머신
- 체결 이벤트 처리
- partial fill 관리
- 중복 주문 방지
- idempotency
- 포지션 reconciliation
- long-running service 안정성
- 강한 타입 기반 도메인 모델링
- 동시성 처리
- 운영/로그/헬스체크

특히 현재 요구사항은 BTC/USDT 선물, 레버리지, 양방향, 시장가/지정가, VPS 24/7 운영을 포함한다. 이 조건에서는 Python-only보다 live path를 Java로 격리하는 것이 장기적으로 더 안전하다.

---

## 5. Java 채택의 문제점

Java 채택은 장점만 있는 선택이 아니다. 다음 비용이 증가한다.

- Python/Java 이중 코드베이스
- 공유 스키마 관리 필요
- 백테스트와 live runtime 불일치 위험
- 개발 속도 저하 가능성
- Docker/CI/CD 복잡도 증가
- Claude Code 작업 지시 난이도 증가
- Java OMS 설계 실패 시 오히려 복잡한 시스템이 될 위험

따라서 Java 사용 범위는 초기부터 강하게 제한한다.

```text
Java는 OMS / Risk / Execution / Reconciliation에만 사용한다.
전략 연구, 백테스트, ML, 리포트는 Python에 둔다.
```

---

## 6. MVP 범위

MVP에는 다음만 포함한다.

- BingX BTC/USDT 선물 단일 상품
- 단순 테스트 전략 1~2개
- Python 백테스트
- Java OMS skeleton
- Java Paper Broker
- Java Risk Gateway
- Java Execution/BingX Adapter skeleton
- Market/Limit order 모델링
- Long/Short position model
- 레버리지 제한 모델링
- 로그/리포트
- kill switch
- paper trading

MVP에서 제외한다.

- 자동학습 실제 구현
- 자동 live 배포
- 완전 무인 운영
- 멀티거래소 실거래
- 복잡한 ML 전략
- HFT
- Kafka/Aeron/Chronicle Queue 같은 고급 메시징
- Kubernetes

---

## 7. 손실 한도 기본값

초기값은 봇에 배정한 운용자본 기준이다.

### Canary Live

| 항목 | 기본값 |
|---|---:|
| 기본 레버리지 | 1x |
| 최대 레버리지 | 2x |
| 1회 주문 최대 notional | 2% |
| 일 손실 한도 | -0.5% |
| 주 손실 한도 | -1.5% |
| 월 손실 한도 | -3% |
| hard stop | -4% |

### Stable Live

| 항목 | 기본값 |
|---|---:|
| 기본 레버리지 | 2x |
| 최대 레버리지 | 3x |
| 1회 주문 최대 notional | 5% |
| 일 손실 한도 | -1% |
| 주 손실 한도 | -3% |
| 월 손실 한도 | -6% |
| hard stop | -8% |
| emergency stop | -10% |

---

## 8. Paper Trading 통과 기준

- 최소 30일, 권장 45일
- 최소 50회 이상 거래
- 치명적 크래시 0건
- 중복 주문 0건
- 포지션 불일치 0건
- 리스크 엔진 우회 0건
- daily report 누락 없음
- kill switch 정상 작동
- paper score 80점 이상

---

## 9. Live 진입 기준

- Paper trading 30일 이상
- Paper score 80점 이상
- 모든 hard gate 통과
- VPS 운영
- API key IP 제한
- withdrawal 권한 없음
- live flag 수동 승인
- leverage hard max 2x
- market order guard 활성화
- kill switch 검증 완료

---

## 10. 아직 결정해야 하는 것

1. BingX의 정확한 상품 코드와 API 모드
2. Hedge mode / one-way mode 중 무엇을 쓸지
3. Cross margin / isolated margin 중 무엇을 쓸지
4. 초기 주문 기본값: limit-first인지 market 허용 우선인지
5. 손절/익절 방식: 거래소 native stop을 쓸지 내부 risk stop을 쓸지
6. Java ↔ Python 공유 스키마 형식: JSON Schema 또는 Protobuf
7. Java strategy runtime을 어디까지 둘지
8. 백테스트와 Java live path의 일치성을 어떻게 테스트할지
9. VPS 위치와 네트워크 지연 측정 기준
10. 알림 채널: Telegram / Discord / Email


---

# PRODUCT_SPEC.md

# 개인용 기관식 BTC/USDT 선물 자동매매 시스템 제품 요구사항 v3

---

## 1. 제품 목표

개인 사용자를 위한 자동매매 시스템을 구축한다. 초기 대상은 BingX 거래소의 BTC/USDT 선물이며, 장기적으로 여러 거래소, 여러 심볼, 여러 자산군으로 확장 가능한 구조를 목표로 한다.

이 시스템은 SaaS가 아니다. 단일 사용자가 VPS 또는 로컬 환경에서 직접 운영하는 개인용 시스템이다. 다만 내부 구조는 업계식 자동매매 시스템의 핵심 원칙을 따른다.

핵심 목표는 단순한 매매 봇이 아니라 다음을 갖춘 장기 운용 가능한 trading system이다.

- 신뢰 가능한 데이터 수집
- 재현 가능한 백테스트
- 전략 연구와 실험 관리
- 리스크 관리
- OMS 기반 주문 관리
- 실거래 실행 엔진
- 포지션/체결 reconciliation
- paper trading
- canary live trading
- 자동학습/모델배포/롤백이 가능한 장기 구조
- Claude 기반 개발 자동화
- LLM 기반 연구/운영 보조

---

## 2. 현재 확정된 제품 범위

| 항목 | 결정 |
|---|---|
| 초기 거래소 | BingX |
| 초기 상품 | BTC/USDT USDT-M Perpetual Futures |
| 방향 | Long / Short 모두 허용 |
| 레버리지 | 포함하되 단계별 제한 |
| 주문 방식 | Market / Limit 둘 다 지원 |
| 타임프레임 | 15m 기본, 5m 확장, 1h regime filter |
| 운영 | VPS 우선 |
| 서비스 형태 | 개인용, 단일 사용자, SaaS 아님 |
| 개발 방식 | Claude Code 기반 바이브코딩 + 엄격한 문서/테스트/리뷰 게이트 |
| 기술스택 | Python + Java Hybrid-lite |

---

## 3. 제품 비목표

다음은 초기 목표가 아니다.

- HFT
- co-location
- tick-level 초저지연 전략
- 초단기 시장조성
- 멀티사용자 SaaS
- 사용자 관리/결제/권한 시스템
- 자동 live 배포
- 완전 무인 실거래
- 대규모 분산 백테스트
- 복잡한 Kubernetes 운영
- 다수 거래소 동시 live trading

---

## 4. 초기 전략 요구사항

초기 전략은 수익을 내기 위한 고급 전략이 아니라 시스템 검증용이다.

허용 예시:

- 이동평균 교차
- 단순 breakout
- RSI mean reversion
- volatility filter가 붙은 단순 trend following

초기 목표:

- 데이터 흐름 검증
- 백테스트 재현성 검증
- 리스크 엔진 검증
- OMS 상태 전이 검증
- paper trading 운영 검증
- live 진입 전 시스템 안정성 확인

---

## 5. 장기 전략 요구사항

장기적으로는 전략 연구 시스템을 통해 다음을 지원한다.

- 여러 전략 추가
- 여러 timeframe 전략
- parameter sweep
- walk-forward validation
- out-of-sample 검증
- 모델/파라미터 버전 관리
- 자동학습 후보 생성
- 자동 백테스트
- paper promotion
- canary live promotion
- 롤백

---

## 6. LLM에 대한 제품 관점

LLM은 초기 MVP에서 실시간 매매 판단자가 아니다.

허용되는 역할:

- 전략 아이디어 생성
- 논문/자료 요약
- 백테스트 결과 해석
- 장애 로그 요약
- 운영 리포트 작성
- 리스크 리뷰
- 코드 구현 보조
- 엣지케이스 탐색

금지되는 역할:

- 직접 주문 생성
- 주문 수량 결정
- 레버리지 변경
- 리스크 한도 완화
- live trading flag 활성화
- 손실 복구 거래 수행

---

## 7. 자동학습/배포/롤백 요구사항

자동학습/배포/롤백은 장기 제품 목표에 포함한다. 그러나 MVP에서는 구현하지 않는다.

MVP에서 해야 하는 일:

- data_version 기록
- feature_version 기록
- strategy_version 기록
- parameter_version 기록
- model_version 필드 준비
- backtest_run_id 기록
- paper_run_id 기록
- deployment_id 기록
- current/previous deployment config 구조 준비

장기적으로 해야 하는 일:

- 정기 재학습
- 자동 검증
- baseline 비교
- paper 자동 승격 후보
- canary live 후보
- 성능 저하 감지
- 자동 중단
- 수동 승인 또는 제한적 자동 롤백

---

## 8. 제품 성공 기준

초기 성공 기준은 수익률이 아니라 안정성이다.

MVP 성공 기준:

- 동일 데이터/파라미터에서 백테스트 결과 재현
- Java OMS order lifecycle 테스트 통과
- risk engine이 모든 주문 후보를 차단/승인
- paper trading 30일 이상 안정 운영
- 중복 주문 0건
- 포지션 불일치 0건
- kill switch 작동
- daily report 생성
- live trading 전에 human approval gate 존재

장기 성공 기준:

- 전략 연구 반복 속도 확보
- 모델/파라미터 배포/롤백 가능
- 실거래 성과와 백테스트/페이퍼 결과 비교 가능
- drawdown 제한 내 장기 운영


---

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


---

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


---

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


---

# RISK_POLICY.md

# 리스크 정책 v3

---

## 1. 핵심 원칙

리스크 엔진은 전략보다 우선한다. 모든 주문 후보는 Java Risk Gateway를 통과해야 한다.

```text
Strategy Signal → OrderIntent → Java Risk Gateway → OMS → Execution
```

Risk Gateway를 우회하는 주문은 허용하지 않는다.

---

## 2. 초기 거래 위험

현재 시스템은 선물, 레버리지, long/short, market order를 포함한다. 따라서 spot-only 봇보다 리스크가 크다.

특히 다음 위험을 관리해야 한다.

- 레버리지 손실 확대
- short position liquidation risk
- 시장가 주문 슬리피지
- position mode 착오
- cross/isolated margin 착오
- reduce-only 누락
- 중복 주문
- API 장애 중 재시도
- WebSocket 이벤트 누락
- 포지션 불일치

---

## 3. 기본 제한값

### Paper Trading

- 레버리지 1x/2x/3x/5x 시뮬레이션 가능
- 실제 손실 없음
- 단, live와 동일한 risk gate 적용

### Canary Live

| 항목 | 기본값 |
|---|---:|
| 기본 레버리지 | 1x |
| 최대 레버리지 | 2x |
| 1회 주문 최대 notional | 운용자본의 2% |
| 심볼 최대 exposure | 운용자본의 10% |
| 일 손실 한도 | -0.5% |
| 주 손실 한도 | -1.5% |
| 월 손실 한도 | -3% |
| hard stop | -4% |

### Stable Live

| 항목 | 기본값 |
|---|---:|
| 기본 레버리지 | 2x |
| 최대 레버리지 | 3x |
| 1회 주문 최대 notional | 운용자본의 5% |
| 심볼 최대 exposure | 운용자본의 20% |
| 일 손실 한도 | -1% |
| 주 손실 한도 | -3% |
| 월 손실 한도 | -6% |
| hard stop | -8% |
| emergency stop | -10% |

---

## 4. 주문 규칙

### Market Order

Market order는 허용하되 제한한다.

허용 조건:

- position exit
- emergency reduce
- liquidity 충분
- max slippage guard 통과
- canary 단계에서는 notional 매우 제한

금지 조건:

- spread 과도
- volatility spike
- WebSocket stale
- orderbook unavailable
- risk state degraded

### Limit Order

기본 주문 방식은 limit-first를 권장한다.

추가 옵션:

- post-only later
- time-in-force 정책
- cancel/replace 정책

---

## 5. Position Mode / Margin Mode

결정 필요:

- Hedge mode vs one-way mode
- Isolated margin vs cross margin

초기 추천:

```text
Hedge mode: long/short를 명확히 분리하려면 유리하지만 복잡도 증가
One-way mode: 단순하지만 양방향 전략 표현이 제한될 수 있음

Isolated margin: 초기 안전성 우선
Cross margin: MVP에서는 비추천
```

초기 추천값:

```text
Isolated margin 우선
Position mode는 BingX API/운영 편의성 확인 후 결정
```

---

## 6. Kill Switch

Kill switch는 다음 조건에서 발동한다.

- 일 손실 한도 도달
- 주 손실 한도 도달
- 월 손실 한도 도달
- 포지션 불일치 발생
- 중복 주문 감지
- WebSocket stale threshold 초과
- API 오류 급증
- risk engine internal error
- 수동 정지 명령

Kill switch 발동 후:

1. 신규 주문 중지
2. open order 취소 시도
3. 포지션 상태 snapshot 저장
4. 알림 전송
5. 수동 확인 전 live 재개 금지

---

## 7. Human Approval 필요 항목

- live trading 활성화
- leverage 상향
- loss limit 완화
- market order 제한 완화
- position mode 변경
- margin mode 변경
- API key 권한 변경
- 새 전략 live 배포
- 자동학습 모델 live 승격
- kill switch 해제


---

# VALIDATION_POLICY.md

# 검증 정책 v3

---

## 1. 검증 원칙

수익률이 높아 보여도 검증을 통과하지 못하면 배포하지 않는다.

검증은 다음 순서를 따른다.

```text
Spec → Unit Test → Backtest → Risk Test → Paper Trading → Canary Live → Stable Live
```

---

## 2. Hard Gate

하나라도 실패하면 배포 금지.

- 테스트 실패
- 재현 불가능한 백테스트
- 수수료/슬리피지 미반영
- 선견 편향 의심
- 데이터 누수 의심
- OOS 실패
- risk limit 위반
- OMS state machine 오류
- 중복 주문 가능성
- position reconciliation 실패
- kill switch 실패
- human approval 없음

---

## 3. 백테스트 검증

필수 포함:

- 동일 입력에서 동일 결과
- 수수료
- 슬리피지
- 거래소 최소 주문 단위
- funding fee later
- liquidation risk later
- out-of-sample
- walk-forward later
- benchmark 비교
- drawdown
- turnover

---

## 4. 편향 관리

### 생존 편향

BTC 단일 심볼에서는 영향이 제한적이지만, 멀티심볼 확장 시 상장/상폐 이력을 관리해야 한다.

### 선견 편향

- closed candle만 사용
- signal timestamp와 execution timestamp 분리
- 현재 봉 종가를 같은 봉 체결에 사용하지 않기
- feature 계산 시 미래 row 접근 금지

### 데이터 스누핑

- 실험 횟수 기록
- 실패한 실험도 저장
- test set 반복 조회 금지
- 후보 전략 생성과 최종 검증 데이터 분리

### 과적합

- 단순 모델 우선
- 파라미터 수 제한
- 여러 기간/국면 검증
- 거래 비용 보수 적용
- paper trading 필수

---

## 5. Java OMS 검증

Java OMS는 다음 테스트를 통과해야 한다.

- New → Accepted → PartiallyFilled → Filled
- New → Accepted → Canceled
- New → Rejected
- Partial fill 후 cancel
- duplicate client order id 거부
- stale order 처리
- retry 후 중복 주문 방지
- WebSocket fill event와 REST query reconciliation
- position side 검증
- leverage/margin 설정 검증
- kill switch 중 신규 주문 거부

---

## 6. Python ↔ Java 일치성 검증

Hybrid 구조에서는 다음을 추가로 검증한다.

- DeploymentManifest schema validation
- Python OrderIntent와 Java OrderIntent parsing 일치
- 동일 signal snapshot 비교
- 백테스트 주문 후보와 Java risk decision 비교
- Java risk rule을 Python validation에서도 사용하거나 동일 case suite 유지

---

## 7. Paper Trading 통과 기준

- 최소 30일, 권장 45일
- 최소 50회 이상 거래
- 치명적 크래시 0건
- 중복 주문 0건
- 포지션 불일치 0건
- risk bypass 0건
- daily report 누락 0건
- kill switch 정상 작동
- paper score 80점 이상

---

## 8. Canary Live 진입 기준

- paper gate 통과
- human approval
- live flag 수동 설정
- API key IP 제한
- withdrawal 권한 없음
- canary risk limit 적용
- rollback target 존재
- incident runbook 존재

---

## 9. Score 사용 원칙

Score는 후보 비교용이다. Hard Gate를 대체하지 않는다.

```text
Hard Gate 실패 = 점수와 무관하게 배포 금지
Hard Gate 통과 = Score로 우선순위 비교
Live 승격 = Human Approval 필요
```


---

# MLOPS_AUTO_TRAIN_DEPLOY_ROLLBACK.md

# 자동학습, 모델배포, 롤백 설계 v3

---

## 1. 원칙

자동학습과 자동배포는 최종 목표에 포함한다. 그러나 MVP에서는 구현하지 않는다.

초기에는 다음만 준비한다.

- 버전 식별자
- 실험 기록
- 백테스트 기록
- paper/live 기록
- deployment manifest
- previous/current config
- rollback 가능성

---

## 2. 필수 식별자

모든 실험과 배포 기록에는 다음을 포함한다.

```text
data_version
feature_version
strategy_version
parameter_version
model_version
backtest_run_id
paper_run_id
live_run_id
deployment_id
```

---

## 3. Deployment Manifest

Python이 배포 후보를 만든다. Java는 manifest를 읽고 검증한다.

예시:

```yaml
deployment_id: dep_2026_07_03_001
strategy_id: btc_trend_v1
strategy_version: 1.0.0
parameter_version: 2026-07-03-a
model_version: none
data_version: bingx_btcusdt_2026_07_03
risk_profile: canary_v1
exchange: bingx
symbol: BTC/USDT:USDT
timeframe: 15m
leverage_max: 2
order_policy: limit_first_market_guarded
status: candidate
previous_deployment_id: dep_previous
```

---

## 4. 자동학습 단계

장기적으로 다음 흐름을 사용한다.

1. 데이터 업데이트
2. feature 생성
3. 후보 모델/파라미터 학습
4. 백테스트
5. OOS 검증
6. walk-forward 검증
7. baseline 비교
8. risk gate
9. paper deployment
10. canary candidate
11. human approval
12. live deployment
13. 모니터링
14. rollback

---

## 5. 모델 점수 체계

점수는 100점 기준이다.

| 항목 | 점수 |
|---|---:|
| 수익성 | 15 |
| 위험 조정 성과 | 20 |
| 강건성 | 20 |
| 거래 비용 내성 | 10 |
| OOS/WF 성과 | 15 |
| 운영 안정성 | 10 |
| 복잡도 패널티 | 10 |

점수 구간:

| 점수 | 판단 |
|---:|---|
| 0~69 | 폐기 또는 보류 |
| 70~79 | 추가 연구 |
| 80~84 | paper 후보 |
| 85~89 | paper 통과 후 canary 후보 |
| 90+ | stable 후보 검토 가능 |

---

## 6. Hard Gate

다음 중 하나라도 발생하면 score와 무관하게 배포 금지.

- 데이터 누수
- 선견 편향
- 수수료/슬리피지 미반영
- OOS 실패
- drawdown limit 초과
- 거래 횟수 비정상 증가
- 리스크 한도 위반 증가
- 재현 불가능
- Java manifest validation 실패
- OMS/risk test 실패

---

## 7. 자동배포 정책

MVP에서는 자동 live 배포 금지.

장기적으로도 기본 원칙은 다음이다.

```text
자동학습 = 가능
자동검증 = 가능
paper 자동 배포 = 조건부 가능
canary live 후보 생성 = 가능
live 승격 = human approval 기본
risk limit 완화 = human approval 필수
```

---

## 8. 롤백 정책

초기 롤백은 단순해야 한다.

```text
current.yaml → previous.yaml
```

Java Trading Engine은 시작 시 다음을 검증한다.

- current deployment 존재
- previous deployment 존재
- schema valid
- risk profile valid
- live flag 승인됨

문제 발생 시:

1. 신규 주문 중단
2. open order 취소 시도
3. 상태 snapshot 저장
4. current를 disabled로 변경
5. previous deployment로 복구 후보 생성
6. 사람 승인 후 재개

---

## 9. 어려운 점 평가

자동학습/배포/롤백은 어렵다. 그러나 MVP에서 구현하지 않고 구조만 준비하면 감당 가능하다.

가장 위험한 실패는 다음이다.

```text
자동학습이 과적합을 자동화하고,
자동배포가 그 과적합 모델을 실거래로 밀어 넣는 것
```

따라서 점수보다 Hard Gate와 paper/canary 단계가 중요하다.


---

# LLM_USAGE_POLICY.md

# LLM 사용 정책 v3

---

## 1. 원칙

LLM은 실시간 매매 판단자가 아니다. LLM은 연구, 운영, 리스크 리뷰, 장애 분석 보조에 사용한다.

---

## 2. 허용 역할

- 전략 아이디어 생성
- 연구 자료 요약
- 뉴스/이벤트 요약
- 시장 regime 설명
- 백테스트 결과 해석
- 과적합/편향 리뷰
- 장애 로그 요약
- Java OMS edge case 탐색
- incident report 작성
- daily report 작성
- Claude Code 기반 구현/리뷰 보조

---

## 3. 금지 역할

- 직접 주문 생성
- 주문 수량 결정
- 레버리지 변경
- 리스크 한도 완화
- live flag 활성화
- API key 권한 변경
- 손실 복구 거래 결정
- human approval 대체

---

## 4. 전략에서의 LLM 사용

초기에는 전략 코어에 LLM을 넣지 않는다.

가능한 확장:

- 뉴스 sentiment feature
- 이벤트 risk filter
- 거래 금지 이벤트 감지
- 전략 후보 생성

단, 모든 LLM 기반 signal은 백테스트와 paper trading 검증을 거쳐야 한다.

---

## 5. 운영에서의 LLM 사용

LLM은 운영 관점에서 유용하다.

예:

- 포지션 불일치 원인 후보 정리
- 주문 거절 로그 요약
- WebSocket 장애 이벤트 분석
- API rate limit 발생 패턴 요약
- kill switch 발동 후 incident memo 작성
- 하루 운용 결과 요약

---

## 6. Java OMS와 LLM

LLM은 Java OMS 설계에서 edge case 탐색에 사용할 수 있다.

예:

- partial fill 후 cancel
- duplicate order id
- retry 중 체결
- WebSocket 누락 후 REST reconciliation
- leverage 설정 실패
- position mode mismatch
- reduce-only 누락

단, LLM 제안은 반드시 테스트로 변환해야 한다.


---

# CLAUDE_WORKFLOW.md

# Claude Code 개발 워크플로우 v3

---

## 1. 기본 원칙

Claude Code는 개발 자동화 도구다. 실거래 승인자는 아니다.

---

## 2. Plan Mode 우선

큰 변경은 항상 Plan mode로 시작한다.

예:

```text
Plan mode로만 진행해줘. 코드는 수정하지 마.
Java OMS의 OrderState machine 설계를 먼저 제안해줘.
Python backtest와 Java live path가 불일치할 수 있는 지점을 찾아줘.
```

---

## 3. CLAUDE.md 핵심 규칙

포함해야 하는 규칙:

- live trading flag 변경 금지
- risk limit 완화 금지
- API key 접근 금지
- Java OMS 변경 시 state machine test 필수
- Order/Risk/Execution 변경 시 human review 필요
- Python/Java schema 변경 시 양쪽 테스트 필수
- 완료 주장 전 테스트 결과 보고

---

## 4. Subagent 구성

추천 subagent:

- quant-researcher
- backtest-engineer
- java-oms-engineer
- risk-manager
- execution-engineer
- security-reviewer
- test-engineer
- docs-pm

---

## 5. Hooks / Permissions

차단해야 할 행동:

- live_trading=true 변경
- .env 읽기/출력
- API key 파일 접근
- broker live order script 실행
- risk limit 완화
- leverage limit 상향
- rm -rf
- curl | sh
- docker privileged

자동 실행:

- Python tests
- Java tests
- schema validation
- risk policy tests
- backtest snapshot tests

---

## 6. PR Workflow

1. Issue 작성
2. Plan mode로 설계
3. 작은 PR 생성
4. Python tests
5. Java tests
6. schema compatibility test
7. Claude review
8. Codex review optional
9. Human approval
10. merge

---

## 7. Claude에게 맡기면 좋은 작업

- 문서 초안
- repo 구조 제안
- Java domain model 초안
- test case 생성
- edge case 목록화
- 백테스트 리포트 생성
- risk policy review
- 장애 로그 요약

---

## 8. Claude에게 맡기면 안 되는 작업

- 실거래 승인
- API key 권한 판단
- 레버리지 상향
- market order guard 완화
- loss limit 완화
- 자동 배포 승인
- kill switch 해제

---

## 9. Hybrid 개발 시 주의

Claude가 Python과 Java를 동시에 수정하면 context가 커지고 오류가 늘 수 있다.

권장 방식:

```text
작업 단위를 분리한다.
1 PR = Python only 또는 Java only 또는 schema only
Python/Java 동시 변경은 schema migration PR로 제한한다.
```


---

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


---

# DECISION_LOG.md

# 의사결정 로그 v3

---

## D001: 초기 거래소

BingX를 초기 거래소로 선택한다.

이유:

- 사용자가 데모/실거래 검증에 BingX를 사용할 계획
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

하지만 canary live에서는 최대 2x, stable live에서도 최대 3x를 기본 hard limit으로 한다.

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
- BingX native API: live path 최종 기준

---

## D009: 자동학습

자동학습은 장기 목표로 포함하되 MVP 구현에서는 제외한다.

초기에는 버전 관리와 manifest 구조만 만든다.

---

## D010: LLM

LLM은 실시간 매매 판단자가 아니다.

연구, 운영, 리스크, 장애 분석 보조로 사용한다.
