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
