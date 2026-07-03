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
