# 00_INDEX — 문서 목차 및 Source-of-Truth Registry

이 문서는 저장소의 문서 목차이자 **어떤 문서가 각 주제의 기준(source of truth)인지**를
정의하는 registry다. 문서 간 내용이 충돌하면 여기에 표시된 우선순위를 따른다.

> 이 PR에서는 실제 파일 이동/rename/삭제를 하지 않는다. 이 문서는 현재 파일 상태를
> 있는 그대로 분류하고, 앞으로의 정리 방향만 제안한다.

---

## 1. 문서 관리 원칙

1. 모든 안전·거버넌스 판단의 최상위 기준은 저장소 루트의 **`CLAUDE.md`** 다.
   docs/ 의 어떤 문서도 `CLAUDE.md` 의 비협상 규칙을 완화할 수 없다.
2. 각 주제에는 단 하나의 **source of truth** 문서가 있다. 나머지는 요약(reference)
   이거나 특정 시점의 snapshot 이다.
3. 요약/snapshot 문서는 source of truth 를 **복제**한 것이므로, 값이 다르면
   source of truth 가 옳다.
4. 새 제품/아키텍처 결정은 반드시 해당 주제의 source of truth 문서와
   `11_DECISION_LOG.md` 에 기록한다. snapshot 문서에 새 결정을 추가하지 않는다.
5. 확정되지 않은 항목은 임의로 확정하지 말고 아래 5절 `결정 필요`로 남긴다.

---

## 2. 상태 정의

| 상태 | 의미 |
|---|---|
| `source of truth` | 해당 주제의 기준 문서. 충돌 시 이 문서가 우선한다. |
| `supporting reference` | source of truth 를 보조하는 문서. 독립적으로 결정을 확정하지 않는다. |
| `snapshot/reference only` | 특정 시점 상태를 합쳐 둔 스냅샷. 계속 갱신하는 주 문서가 아니다. |
| `archive candidate` | 역할이 끝났거나 중복되어 후속 PR에서 archive 로 옮기는 것을 제안. |
| `decision required` | 아직 확정되지 않아 문서로 못 박으면 안 되는 항목. |

---

## 3. 주제별 Source-of-Truth 매핑

| 영역 | Source of truth | 보조/중복 문서 | 비고 |
|---|---|---|---|
| Governance / 안전 규칙 | `../CLAUDE.md` | `docs/SECRET_AND_GIT_POLICY.md`, `docs/09_CLAUDE_WORKFLOW.md` | 최상위 권위. 다른 문서가 완화 불가 |
| Project overview | `docs/00_MASTER_SUMMARY.md` | `../README.md` | README 는 저장소 진입점 요약 |
| Product | `docs/01_PRODUCT_SPEC.md` | `docs/00_MASTER_SUMMARY.md` | |
| System architecture | `docs/02_SYSTEM_SPEC.md` | `docs/00_MASTER_SUMMARY.md` | |
| Architecture decision (Java hybrid) | `docs/03_HYBRID_JAVA_ARCHITECTURE_DECISION.md` | `docs/11_DECISION_LOG.md` (D007) | ADR 가 상세 근거 |
| MVP scope / roadmap | `docs/04_MVP_SCOPE_AND_ROADMAP.md` | `docs/00_MASTER_SUMMARY.md` §6 | |
| Risk / leverage / loss limit | `docs/05_RISK_POLICY.md` | `docs/00_MASTER_SUMMARY.md` §7·§9 | **리스크 수치는 05 가 기준.** 00 요약과 다르면 05 우선 |
| Validation / hard gate | `docs/06_VALIDATION_POLICY.md` | `docs/07_MLOPS_...` §6 | |
| MLOps / train / deploy / rollback | `docs/07_MLOPS_AUTO_TRAIN_DEPLOY_ROLLBACK.md` | `docs/04_...`, `docs/01_...` §7 | MVP 미구현, 구조만 |
| LLM usage policy | `docs/08_LLM_USAGE_POLICY.md` | `../CLAUDE.md` (LLM Usage Policy 절) | |
| Claude / dev workflow | `docs/09_CLAUDE_WORKFLOW.md` | `../CLAUDE.md` (Development Workflow 절), `docs/claude/CLAUDE_OPERATING_MODEL.md`, `docs/claude/PM_HANDOFF.md` | PR #3 에서 v4로 갱신. 상세 실행 절차는 `CLAUDE_OPERATING_MODEL.md`(supporting reference), 현재 상태 스냅샷은 `PM_HANDOFF.md`(supporting reference / current snapshot, source of truth 아님) |
| Project reviewer subagents | `.claude/agents/*.md` (executable definitions) | `docs/09_CLAUDE_WORKFLOW.md` §F.1 (normative workflow), `docs/claude/CLAUDE_OPERATING_MODEL.md` §13 (supporting procedure) | PR #4 에서 구현. 5개 read-only reviewer(`architecture-reviewer`, `java-oms-reviewer`, `python-research-reviewer`, `risk-reviewer`, `test-reviewer`). implementation agent 아님. CodeRabbit/security-gates 대체 아님 |
| Open questions / risks | `docs/10_OPEN_QUESTIONS_AND_RISKS.md` | `docs/00_MASTER_SUMMARY.md` §10 | |
| Decision log | `docs/11_DECISION_LOG.md` | — | 확정된 결정의 기준 기록 |
| Setup (WSL/local) | `docs/LOCAL_SETUP_WSL.md` | `../README_BOOTSTRAP.md` | |
| Secret / Git policy | `docs/SECRET_AND_GIT_POLICY.md` | `../CLAUDE.md`, `../.gitignore` | |
| Review governance / merge gate | `docs/claude/CODERABBIT_REVIEW_MODEL.md` | `.coderabbit.yaml`, `.github/workflows/security-gates.yml`, `scripts/ci/security_gates.py` | PR #2 에서 deterministic security gate(`security-gates` required check) 추가(8절). PR #3 에서 Latest-Head Review Completeness 추가(9절) |

---

## 4. Snapshot / Reference-only 문서

- **`docs/12_ALL_IN_ONE_SPEC.md`** — `snapshot/reference only`.
  docs 00–11 의 내용을 하나로 합쳐 둔 스냅샷이다. 계속 수정하는 주 문서가 아니다.
  새 결정·변경은 개별 source-of-truth 문서에 하고, 이 파일은 참고용으로만 둔다.
  값이 개별 문서와 다르면 **개별 문서가 우선**한다.

---

## 5. Archive 후보 (이번 PR에서는 이동하지 않음)

아래는 중복/일회성 성격이라 후속 정리 PR에서 `docs/archive/` 등으로 옮기는 것을
제안한다. **이번 PR에서는 실제 이동하지 않는다.**

| 파일 | 사유 | 제안 |
|---|---|---|
| `docs/12_ALL_IN_ONE_SPEC.md` | 00–11 전체 중복 스냅샷 | 후속 PR에서 `docs/archive/` 이동 검토 |
| `../README_BOOTSTRAP.md` | 초기 1회성 부트스트랩 가이드, `LOCAL_SETUP_WSL.md` 와 상당 부분 중복 | 후속 PR에서 archive 또는 setup 문서로 통합 검토 |

---

## 6. 결정 필요 (Decision Required)

아래는 문서상 아직 확정되지 않았다. 코드나 설정에서 임의로 확정하지 않는다.
출처: `docs/10_OPEN_QUESTIONS_AND_RISKS.md`, `docs/00_MASTER_SUMMARY.md` §10,
`docs/05_RISK_POLICY.md` §5.

1. BingX 정확한 상품 코드 / API symbol
2. Position mode — hedge vs one-way
3. Margin mode — isolated(추천) vs cross
4. 초기 주문 정책 세부 — limit-first + guarded market(추천)이나 세부 미확정
5. 손절/익절 방식 — 거래소 native stop vs 내부 risk stop
6. Python↔Java 공유 스키마 형식 — JSON Schema(v0.1 추천) vs Protobuf(후기)
7. Java strategy runtime 범위 — signal 까지 Java vs Python signal 소비
8. 백테스트 ↔ Java live path 일치성 테스트 방법
9. VPS 위치 / 네트워크 지연 측정 기준
10. 알림 채널 — Telegram(추천) / Discord / Email

---

## 7. 문서 변경 규칙

1. 주제 내용을 바꾸면 해당 주제의 **source of truth 문서**를 수정한다.
2. 확정된 결정은 `11_DECISION_LOG.md` 에 항목으로 추가한다.
3. 문서의 역할(status)을 바꾸면 이 `00_INDEX.md` 의 표를 함께 갱신한다.
4. `12_ALL_IN_ONE_SPEC.md` 에는 새 결정을 추가하지 않는다(snapshot).
5. 리스크·레버리지 수치는 항상 `05_RISK_POLICY.md` 를 기준으로 한다.
6. 안전·거버넌스 규칙은 `CLAUDE.md` 를 완화하지 않는 범위에서만 추가한다.

---

## 8. 향후 권장 docs 구조 (제안, 이번 PR 범위 아님)

```text
docs/
  00_INDEX.md                     # 본 문서 (registry)
  00_MASTER_SUMMARY.md            # overview (source of truth)
  01_PRODUCT_SPEC.md
  02_SYSTEM_SPEC.md
  03_HYBRID_JAVA_ARCHITECTURE_DECISION.md
  04_MVP_SCOPE_AND_ROADMAP.md
  05_RISK_POLICY.md
  06_VALIDATION_POLICY.md
  07_MLOPS_AUTO_TRAIN_DEPLOY_ROLLBACK.md
  08_LLM_USAGE_POLICY.md
  09_CLAUDE_WORKFLOW.md
  10_OPEN_QUESTIONS_AND_RISKS.md
  11_DECISION_LOG.md
  LOCAL_SETUP_WSL.md
  SECRET_AND_GIT_POLICY.md
  claude/
    CODERABBIT_REVIEW_MODEL.md    # merge gate 모델
    CLAUDE_OPERATING_MODEL.md     # 상세 operating procedure (supporting reference)
    PM_HANDOFF.md                 # 현재 상태 스냅샷 (supporting reference / snapshot)
  archive/                        # (후속 PR) 12_ALL_IN_ONE_SPEC.md 등 이동 대상

.claude/
  agents/                          # project reviewer subagents (5 files, read-only)
```

> 위 구조는 제안이며, 실제 파일 이동/생성은 후속 PR에서 별도로 진행한다.
