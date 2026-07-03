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
