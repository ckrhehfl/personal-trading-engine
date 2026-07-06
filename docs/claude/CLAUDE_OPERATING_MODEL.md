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

대상: **R2**, **R3**, **Python backtest**, 범위가 큰 **R1**.

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

Claude Code CLI **2.1.201** 기준(`claude --version`), `claude --help` 실 출력에서
확인한 관련 플래그는 다음과 같다. 이 목록은 해당 버전의 실제 동작에 근거한
것이며, CLI가 업데이트되면 아래 플래그명·기본값이 바뀔 수 있으므로 **사용 전
`claude --help`로 현재 버전 기준 재확인**한다.

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
- project reviewer subagents 5개 (`.claude/agents/*.md`, §13 참고)
- project skills 5개 (`.claude/skills/review-*/SKILL.md`, manual-only reviewer
  wrapper, §14 참고)
- project 수준 deny permission rules + PreToolUse policy guard hook
  (`.claude/settings.json`, `.claude/hooks/policy_guard.py`, §14 참고)

**Planned (미구현):**

- implementation agents (구현자 subagent)
- shared schema skeleton
- Java/Python 코드 skeleton

최신 상태는 `docs/claude/PM_HANDOFF.md`를 확인한다.

---

## 13. Project reviewer subagents

`.claude/agents/*.md`에 5개의 **read-only reviewer** subagent가 정의되어
있다. 전부 reviewer/analyst이며 구현자(implementer)가 아니다.

| Agent | 역할 | 주요 risk coverage | 호출 시점 |
|---|---|---|---|
| `architecture-reviewer` | architecture/ADR/system-boundary/shared-contract 검토 | R2, 큰 R1, cross-language contract | architecture/ADR/schema contract 변경 시 |
| `java-oms-reviewer` | Java OMS/상태 머신/실행 lifecycle/reconciliation 검토 | R3 | Java OMS/state lifecycle 변경 시 |
| `python-research-reviewer` | Python research/backtest 재현성·편향·leakage·live-order boundary 검토 | Python backtest tier | Python research/backtest 변경 시 |
| `risk-reviewer` | risk/leverage/exposure/loss-limit/kill-switch/live 설정 검토 | R3/R4 boundary, R4 escalation | risk/R4-adjacent 변경 시 |
| `test-reviewer` | 테스트 충분성·regression 진위·edge case coverage 검토 | R0~R3 지원, domain-independent | 모든 PR의 테스트 검토 시 |

### Tool boundary (모든 agent 공통)

모든 agent의 frontmatter는 정확히 다음을 사용한다:

```yaml
tools: Read, Grep, Glob
model: inherit
```

`Write`, `Edit`, `Bash`, `Agent`(nested agent), `Skill`, 모든 `mcp__*` tool은
tool pool에 존재하지 않는다. `permissionMode`, `mcpServers`, `hooks` 등의
field도 사용하지 않는다 — 이 field들을 아예 쓰지 않음으로써, parent session이
`--dangerously-skip-permissions`로 실행되더라도 이 agent들의 capability
surface는 영향을 받지 않는다.

이것이 "절대적인 sandbox"라는 뜻은 아니다. 단지 **사용 가능한 tool 표면
자체를 의도적으로 read-only 3종(`Read`, `Grep`, `Glob`)으로 최소화**했다는
뜻이며, 이 최소화가 permission mode와 무관하게 유지된다는 뜻이다.

### 위임 원칙

- 이 5개 agent는 **reviewer/analyst**이며 최종 merge authority가 아니다.
- CodeRabbit/`security-gates`를 대체하지 않는다 — 병행 층이다.
- open decision(`docs/00_INDEX.md` §6)을 확정하지 않는다. 확정이 필요하면
  `DECISION_REQUIRED`로 보고한다.
- R4 인접 변경은 기본적으로 `BLOCKED` 또는 `DECISION_REQUIRED`로 취급한다.
- 구체적인 delegation table은 `docs/09_CLAUDE_WORKFLOW.md` §F.1을 본다.

---

## 14. Project skills와 permission/hook guardrail (PR #5)

### 14.1 Skill invocation model

이 저장소는 project custom command를 `.claude/commands/`가 아니라
`.claude/skills/<name>/SKILL.md` 형식으로 구현한다. 두 형식은 현재 Claude Code
버전에서 동등하게 로드되며 파일 레이아웃만 다르다. `.claude/skills/`를 택한
이유:

- reviewer subagent를 감싸는 wrapper(`context: fork` + `agent:`)를 표현하는
  frontmatter 필드가 skill 형식에 있다.
- 향후 skill이 늘어날 경우 `references/`, `examples/`, `scripts/` 하위 구조로
  확장 가능하다(이번 PR에서는 사용하지 않음).

F.1의 5개 read-only reviewer subagent 각각을 감싸는 5개 manual-only skill이
있다(`docs/09_CLAUDE_WORKFLOW.md` §F.2 표 참고). 모든 skill은:

- `disable-model-invocation: true` — model이 자동으로 호출할 수 없다. 사용자가
  `/review-*`를 직접 입력해야 실행된다.
- `context: fork` — 격리된 fork 세션에서 실행되며 대응하는 `agent:`를 지정한다.
- `allowed-tools` 사전 승인을 사용하지 않는다 — 감싸는 reviewer agent 자체의
  tool boundary(`Read`, `Grep`, `Glob`)를 그대로 상속한다.
- side effect가 없다 — 파일을 만들거나 수정하지 않고, commit/push/PR/merge
  action을 수행하지 않는다.

이 skill들은 **implementation agent가 아니다.** 구현자 subagent는 여전히
미구현이며(§12), 이 PR의 범위 밖이다.

### 14.2 Deny rules와 PreToolUse hook의 역할 분담

`.claude/settings.json`은 두 개의 안전 계층을 추가한다:

1. **`permissions.deny`** — Claude Code의 permission engine이 특정
   `Tool(specifier)` 패턴을 사전에 거부한다. `permissions.allow`/`.ask`,
   `defaultMode`, `disableBypassPermissionsMode`는 이 PR에서 설정하지 않는다
   (기존 동작을 완화하거나 확장하지 않고, 오직 deny만 추가한다).
2. **PreToolUse policy guard hook** (`.claude/hooks/policy_guard.py`,
   matcher `Bash|Edit|Write`) — permission 패턴만으로 표현하기 어려운 동적
   판단(예: compound bash 명령, 파일 내용 검사)을 결정론적으로 수행한다.

두 계층의 역할은 다르다: deny rule은 **정적 패턴**(경로, 정확한 명령 prefix)만
표현할 수 있고, hook은 **명령 내용/파일 내용을 파싱**해 판단할 수 있다. 이
때문에 예를 들어 "docs/05_RISK_POLICY.md 수정 금지"는 deny rule만으로 충분하지만,
"live trading flag가 파일 내용에 true로 설정되는지"는 hook이 아니면 판단할 수
없다. 이 PR의 hook은 `scripts/ci/security_gates.py`가 이미 확인하는 것과 일부
겹치는 패턴(예: live trading flag, pipe-to-shell install)도 의도적으로
중복 검사한다 — pre-execution 층(hook)과 pre-merge 층(`security-gates`)이
서로 다른 시점에 독립적으로 동작하는 defense-in-depth이기 때문이다.

### 14.3 `--dangerously-skip-permissions`에서의 운영 원칙

§4에서 이미 정의한 대로, permission bypass 모드 자체는 safety boundary가
아니다. 이 PR이 추가하는 project 수준 explicit deny rule과 PreToolUse hook은
그 원칙을 실제로 뒷받침하는 장치다: `--dangerously-skip-permissions`로 parent
session이 실행되어 매 tool 호출마다 확인창이 뜨지 않더라도, project
`.claude/settings.json`에 등록된 deny rule과 hook은 계속 평가된다. 즉 이
guardrail들은 "확인창을 생략한다"는 bypass 모드의 의미와 별개로 유지되는
project 수준 정책이다.

단, 이것이 완전한 sandbox라는 뜻은 아니다. 알려진 한계:

- hook은 `shlex` 기반 best-effort 패턴 매처이며 **완전한 shell parser가
  아니다.** 의도적으로 난독화된 명령(예: 변수 치환, 중첩 서브셸, 문자열 분할로
  쪼갠 명령)은 통과할 수 있다.
- 파일 경로/내용 검사는 hook에 전달된 `tool_input`에 근거하며, 간접적인
  subprocess 호출(예: 다른 스크립트를 통해 파일을 쓰는 경로)은 이 model-level
  hook의 검사 대상이 아니다.
- `.claude/settings.json`/`.claude/hooks/policy_guard.py` 자체의 무결성은
  아직 `security-gates`가 별도로 고정(freeze)하지 않는다 — 즉 이 파일들이
  후속 PR에서 약화되지 않도록 보장하는 기계적 장치는 아직 없다.
- PreToolUse hook은 `Bash|Edit|Write`에만 등록된다. `MultiEdit`/`NotebookEdit`
  tool을 통한 파일 변경은 `permissions.deny`의 경로 기반 규칙(secret path
  차단)만 적용받고, hook의 내용 검사(live trading flag 탐지)는 적용받지 않는다.
- `context: fork` + `agent:`가 감싸는 reviewer subagent의 tool 제약을 forked
  실행 컨텍스트에 구조적으로 강제하는지는 실제 라이브 호출로 경험적으로
  검증하지 않았다.
- hook timeout(5초) 또는 `python3` spawn 실패 시 harness가 fail-open으로
  처리하는지 fail-closed로 처리하는지 확인하지 않았다 — harness 레벨 동작이며
  `policy_guard.py`의 통제 범위 밖이다.

### 14.4 Implemented/planned 갱신

§12의 Implemented 목록에 project skills 5개와 project 수준 deny
rule/PreToolUse hook을 추가했다. Skills/hooks/permissions는 더 이상 "Planned"가
아니다. 최신 상태는 `docs/claude/PM_HANDOFF.md`를 확인한다.
