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
