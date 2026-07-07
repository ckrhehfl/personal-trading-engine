# 00_INDEX — 문서 목차 및 Source-of-Truth Registry

이 문서는 저장소 문서의 navigation source of truth다. 충돌 시 아래 주제별 기준 문서를 따른다.

## 1. 관리 원칙

1. 최상위 운영 기준은 루트 `CLAUDE.md`다.
2. 각 주제에는 하나의 source of truth만 둔다.
3. supporting reference와 snapshot은 결정을 독립적으로 확정하지 않는다.
4. 새 제품·아키텍처 결정은 해당 기준 문서와 `11_DECISION_LOG.md`에 기록한다.
5. 미확정 항목은 4절에만 유지한다.

## 2. Source-of-Truth Registry

| 영역 | Source of truth | Supporting reference |
|---|---|---|
| Governance | `../CLAUDE.md` | `docs/09_CLAUDE_WORKFLOW.md` |
| Overview | `docs/00_MASTER_SUMMARY.md` | `../README.md` |
| Product | `docs/01_PRODUCT_SPEC.md` | `docs/00_MASTER_SUMMARY.md` |
| System architecture | `docs/02_SYSTEM_SPEC.md` | `docs/00_MASTER_SUMMARY.md` |
| Java hybrid ADR | `docs/03_HYBRID_JAVA_ARCHITECTURE_DECISION.md` | `docs/11_DECISION_LOG.md` D007 |
| Shared contracts | `../schemas/v0.1/*.schema.json` | `../schemas/README.md`, D011 |
| MVP roadmap | `docs/04_MVP_SCOPE_AND_ROADMAP.md` | `docs/00_MASTER_SUMMARY.md` |
| Policy | `docs/05_RISK_POLICY.md` | `docs/00_MASTER_SUMMARY.md` |
| Validation | `docs/06_VALIDATION_POLICY.md` | `docs/07_MLOPS_AUTO_TRAIN_DEPLOY_ROLLBACK.md` |
| MLOps | `docs/07_MLOPS_AUTO_TRAIN_DEPLOY_ROLLBACK.md` | `docs/04_MVP_SCOPE_AND_ROADMAP.md` |
| LLM policy | `docs/08_LLM_USAGE_POLICY.md` | `../CLAUDE.md` |
| Development workflow | `docs/09_CLAUDE_WORKFLOW.md` | `docs/claude/CLAUDE_OPERATING_MODEL.md`, `docs/claude/PM_HANDOFF.md` |
| Open questions | `docs/10_OPEN_QUESTIONS_AND_RISKS.md` | `docs/00_MASTER_SUMMARY.md` |
| Decision log | `docs/11_DECISION_LOG.md` | — |
| Local setup | `docs/LOCAL_SETUP_WSL.md` | `../README_BOOTSTRAP.md` |
| Git policy | `docs/SECRET_AND_GIT_POLICY.md` | `../CLAUDE.md` |
| Review governance | `docs/claude/CODERABBIT_REVIEW_MODEL.md` | `.coderabbit.yaml`, `.github/workflows/security-gates.yml` |
| Reviewer agents | `.claude/agents/*.md` | `docs/09_CLAUDE_WORKFLOW.md` §F.1 |
| Reviewer skills | `.claude/skills/review-*/SKILL.md` | `docs/09_CLAUDE_WORKFLOW.md` §F.2 |
| Project settings | `.claude/settings.json`, `.claude/hooks/policy_guard.py` | `docs/claude/CLAUDE_OPERATING_MODEL.md` §14 |

D011은 MVP v0.1 공유 계약 형식으로 JSON Schema Draft 2020-12를 선택했다. Protobuf는 측정 가능한 필요가 생길 때까지 연기한다.

## 3. Snapshot / archive 후보

- `docs/12_ALL_IN_ONE_SPEC.md` — snapshot/reference only
- `../README_BOOTSTRAP.md` — archive 후보

Snapshot과 supporting reference가 개별 기준 문서와 다르면 개별 기준 문서가 우선한다.

## 4. Decision Required

현재 미확정 항목은 다음 9개다.

1. BingX 정확한 API symbol
2. Position mode
3. Margin mode
4. 초기 주문 정책 세부
5. 손절/익절 방식
6. Java strategy runtime 범위
7. 백테스트 ↔ Java trading path 일치성 검증 방법
8. VPS 위치 / 네트워크 지연 기준
9. 알림 채널

상세 내용은 `docs/10_OPEN_QUESTIONS_AND_RISKS.md`가 기준이다.

## 5. 변경 규칙

1. 주제 변경은 해당 source of truth를 수정한다.
2. 확정 결정은 `11_DECISION_LOG.md`에 기록한다.
3. 문서 역할 변경 시 이 registry를 함께 갱신한다.
4. `12_ALL_IN_ONE_SPEC.md`에 새 결정을 추가하지 않는다.
5. `CLAUDE.md`를 다른 문서가 완화할 수 없다.
