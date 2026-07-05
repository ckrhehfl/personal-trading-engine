# Claude Operating Model

**Status:** supporting reference (detailed operating procedure)
**권위 순서:** `../../CLAUDE.md` > `../09_CLAUDE_WORKFLOW.md` > 이 문서. 충돌 시
항상 앞쪽이 우선하며, 이 문서가 앞쪽 두 문서를 완화할 수 없다.

이 문서는 비전문가 owner와 Claude Code가 실제로 함께 작업을 진행하는 절차를
정의한다. `docs/09_CLAUDE_WORKFLOW.md`가 "무엇을 어떤 순서로"를 정의한다면,
이 문서는 "실제로 어떻게"를 정의한다.

---

## 1. 목적과 권위

- 이 문서는 **detailed operating procedure**이며 **supporting reference**다.
- 제품/리스크/아키텍처 결정을 확정하는 문서가 아니다. 그런 결정은 각 주제의
  source-of-truth 문서(`docs/00_INDEX.md` §3 참고)와 `docs/11_DECISION_LOG.md`에만
  기록한다.
- `CLAUDE.md`와 `docs/09_CLAUDE_WORKFLOW.md`와 충돌하면 이 문서가 진다.

---

## 2. 역할 분담

| 주체 | 역할 |
|---|---|
| **Human owner** | 제품/리스크 결정, 고위험 승인 여부 판단, final merge button. 코드를 한 줄씩 판단하는 역할은 아니다. |
| **Claude Code** | inspect → plan → edit → test → commit → push → PR → 유효한 리뷰 지적 수정 → 최종 상태 보고. **merge하지 않는다.** |
| **CodeRabbit** | 1차 독립 AI 리뷰어. 위험/버그/정책 위반을 지적하고 pre-merge check를 수행한다. |
| **`security-gates`** | 결정론적(비AI) merge gate. `.github/workflows/security-gates.yml` + `scripts/ci/security_gates.py`. |
| **Second AI reviewer** | 고위험 변경(§9)에 대한 두 번째 독립 리뷰 후보. 예: 별도 Claude Code 세션, Codex, 또는 동등한 reviewer. 자동 트리거는 아직 없다. |
| **GitHub branch protection** | 위 gate들을 실제로 강제하는 장치. required checks, approval count, conversation resolution 등. |

---

## 3. Claude autonomy modes

### `AUTONOMOUS_EXECUTION`

대상: Risk class **R0**, scope가 명확한 **R1**.

Claude가 branch/worktree 생성 → edit → test → commit → push → PR 생성까지
사람의 중간 승인 없이 수행할 수 있다.

### `PLAN_FIRST`

대상: **R2**, **R3**, 범위가 큰 **R1**.

구현 전에 plan과 영향 범위를 먼저 고정한다. 승인이 필요한지는 risk class와
변경 범위로 판단하며, 애매하면 승인을 받는 쪽을 택한다.

### `BLOCKED`

대상: **R4**, secret 접근, live 활성화, risk 완화, leverage 상향, Risk Gateway
우회, Python 직접 live order, 미검증 installer 실행 등.

이 경우 작업을 중단하고 무엇이 필요한 결정인지만 보고한다. 임의로 진행하지
않는다.

Risk class 정의는 `docs/09_CLAUDE_WORKFLOW.md` §C를 기준으로 한다.

---

## 4. `--dangerously-skip-permissions` 사용 원칙

현재 로컬 CLI(`claude --help` 기준, 2.1.201)에서 실제 지원하는 관련 플래그:

- `--dangerously-skip-permissions` — 모든 permission 확인을 우회한다.
- `--allow-dangerously-skip-permissions` — 위 우회를 "옵션으로" 활성화한다(기본값 아님).
- `--permission-mode <mode>` — `acceptEdits`/`auto`/`bypassPermissions`/`manual` 등.

**이 bypass 모드 자체는 safety boundary가 아니다.** 아래 외부 guardrail이 이미
갖춰져 있을 때만, 저/중위험(R0/R1) 작업에 한해 사용 가능하다:

- trusted repository (본 저장소)
- dedicated worktree(§5)
- bounded task prompt(명시적 scope와 금지 목록)
- branch protection (`CodeRabbit` + `security-gates` required)
- secret scanning / push protection 활성화

이 모드를 켜도 다음은 여전히 무효화되지 않는다:

- `.env`/secret/live/risk 관련 금지 (`CLAUDE.md` 비협상 규칙)
- R4 작업의 `BLOCKED` 상태
- required checks / branch protection

즉, bypass mode는 "매 파일 수정마다 확인창을 띄우지 않는다"는 의미이지,
"R4를 자동 승인한다"는 의미가 아니다. Untrusted repository에서는 사용하지
않는다.

---

## 5. Worktree / branch 모델

- `main`에서 직접 작업하지 않는다.
- 한 작업 = 한 branch/worktree.
- 기존에 열려 있는 PR에 대한 fix는 **같은 worktree/branch**에 commit한다.
  새 fix라고 해서 새 PR을 만들지 않는다(PR #2의 fix commit들 참고).
- stale worktree 정리는 이 작업의 일부가 아니라 **별도의 사용자 승인 작업**이다.
- `.claude/worktrees/**` 디렉터리 자체(다른 작업의 checkout)는 제품 파일로 commit하지 않는다.

Naming 권장: `worktree-<task-slug>` (예: `worktree-security-gates`,
`worktree-claude-operating-model`).

---

## 6. Prompt contract

Claude를 실행하는 prompt는 최소 다음을 포함해야 한다:

- goal
- repo/branch
- authority docs (어떤 문서가 기준인지)
- allowed files
- forbidden files/actions
- acceptance criteria
- validation commands
- commit/PR requirements
- review handling 방침
- final report format
- merge 금지 여부

---

## 7. Verification contract

완료를 주장하기 전에 반드시 보고한다(`CLAUDE.md`의 Required Verification과 동일):

- files changed
- tests added
- tests run
- test results
- `security-gates` status
- CodeRabbit status
- remaining risks
- human approval needed
- PR URL
- merge readiness

---

## 8. Review completeness contract

PR #2에서 얻은 교훈을 공식 규칙으로 고정한다: **required checks green +
CodeRabbit formal APPROVED만으로는 충분하지 않다.** 상세 기준과 체크리스트는
`docs/claude/CODERABBIT_REVIEW_MODEL.md` §9 "Latest-Head Review Completeness"를
따른다(이 문서는 요약만 제공하며, 그 문서가 review/merge gate의 기준이다).

핵심만 요약:

- 판정은 항상 **현재 head SHA** 기준이다.
- 새 commit이 생기면 이전 판정은 즉시 무효가 되고, 처음부터 다시 확인한다.
- formal `APPROVED`와 별도로 `COMMENTED` review의 outside-diff finding이
  같은 head에 함께 존재할 수 있다 — 반드시 review submission 전체를 본다.

---

## 9. Second reviewer policy

다음 변경은 **second AI reviewer 필요 후보**다:

- `security-gates` 자체 변경 (`.github/workflows/security-gates.yml`,
  `scripts/ci/security_gates.py`)
- 공유 schema의 breaking change
- Java OMS state machine
- Java Risk Gateway
- Execution/Exchange adapter
- cross-language contract migration
- risk policy 변경(`docs/05_RISK_POLICY.md`)

현재 이 목록에 대한 **자동 trigger는 구현되어 있지 않다.** Codex 또는 다른
GitHub App 연동도 이번 범위가 아니다. 지금은 "이런 변경에는 두 번째 리뷰가
필요하다"는 문서상 요구사항만 정의한다.

---

## 10. Stop conditions

다음 중 하나라도 발생하면 즉시 중단한다:

- source docs 간 충돌 발견
- `결정 필요` 항목을 확정해야만 진행 가능한 경우
- secret이 필요한 경우
- live/risk 변경이 필요한 경우
- required gate 약화가 필요해 보이는 경우
- 작업 범위가 예상보다 커짐(scope creep)
- base/`main`이 예상치 않게 바뀜
- 기존 사용자 변경과 충돌

중단 시 보고 형식: **무엇을 완료했는지 / 무엇이 blocker인지 / 필요한 결정만**.
불필요한 추측이나 임의 확정을 하지 않는다.

---

## 11. Session resume / handoff

새 세션(또는 새 대화)은 다음 순서로 읽는다:

1. `../../CLAUDE.md`
2. `../00_INDEX.md`
3. `PM_HANDOFF.md`
4. 해당 task의 source-of-truth 문서

`PM_HANDOFF.md`는 source-of-truth를 복제하거나 독자적으로 변경하지 않는다.
어디까지나 "지금 상태가 무엇인지"를 요약하는 스냅샷이다.

---

## 12. 현재 구현 상태

**Implemented:**

- public repository
- branch protection (`CodeRabbit` + `security-gates` required)
- secret scanning
- push protection
- CodeRabbit 리뷰
- `security-gates` deterministic check

**Planned (미구현):**

- project subagents
- skills / slash commands
- hooks / permissions
- shared schema skeleton
- Java/Python 코드 skeleton

최신 상태는 `docs/claude/PM_HANDOFF.md`를 확인한다.
