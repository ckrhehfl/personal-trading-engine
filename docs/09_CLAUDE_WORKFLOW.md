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
