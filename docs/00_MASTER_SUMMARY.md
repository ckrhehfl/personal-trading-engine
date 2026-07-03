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
