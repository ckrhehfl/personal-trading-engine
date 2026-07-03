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
