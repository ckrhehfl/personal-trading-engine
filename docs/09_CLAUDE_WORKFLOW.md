# CLAUDE_WORKFLOW.md

# Claude Code 개발 워크플로우 v4

이 문서는 Claude Code 개발 workflow의 **normative source of truth**다. 무엇을
어떤 순서로 진행하는지 정의한다. 실제로 그 순서를 어떻게 수행하는지의 상세
절차는 `docs/claude/CLAUDE_OPERATING_MODEL.md`(supporting reference)를 본다.

> v3 대비: v3의 §4 Subagent, §5 Hooks/Permissions는 **아직 구현되지 않은
> 추천안**이었다. 실제로 존재하는 required check는 `CodeRabbit`과
> `security-gates` 뿐이다(PR #1, #2). v4는 실제 구현 상태와 계획을 명확히
> 구분한다. 자세한 배경은 `docs/claude/PM_HANDOFF.md`.

---

## A. 기본 원칙

| 주체 | 역할 |
|---|---|
| **Claude Code** | 구현자/문서 작성자. 계획하고, 코드/문서를 만들고, self-review 후 PR을 낸다. |
| **CodeRabbit** | 1차 독립 AI 리뷰어. |
| **`security-gates`** | 결정론적(비AI) merge gate. |
| **Human(owner)** | 제품/리스크 결정권자, 최종 merge 버튼. 코드 한 줄 단위 판독자는 아닐 수 있다. |

핵심: human이 코드 세부를 다 읽지 못할 수 있다는 전제 하에, **기계적 gate(CodeRabbit
required check + security-gates)를 사람의 판단보다 우선**한다. 상세 역할·권한
모델은 `docs/claude/CLAUDE_OPERATING_MODEL.md` §2를 본다.

---

## B. Task intake

사소하지 않은 작업(제품 코드, CI/보안 도구, 문서 구조 변경 등)은 가능하면
**GitHub Issue 또는 명시적 task packet**을 canonical task로 삼는다.

Task packet 최소 항목:

- Goal
- In scope
- Out of scope
- Source-of-truth docs
- Risk class (아래 C절)
- Acceptance criteria
- Verification
- Required reviewers
- Stop conditions

예외: 사용자 prompt 자체가 충분히 구체적인 **작은 docs/bootstrap 작업**(PR #1~#3처럼)은
issue 없이 진행할 수 있다. 그러나 이후 제품 코드(schema/Java/Python) 작업은
**issue-first를 기본**으로 한다.

---

## C. Risk class

| Class | 대상 예시 | Claude autonomy |
|---|---|---|
| **R0** — docs/reference only | 문서 index, non-policy docs, 오타/링크 수정 | plan → edit → validate → commit → push → PR 까지 자율 수행 가능 |
| **R1** — repo/tooling/governance | CI, security gates, Claude 설정, (향후) hooks/permissions | 작은 scope 내 구현 가능. 더 강한 리뷰 필요. security tooling 변경은 second review 후보(`docs/claude/CLAUDE_OPERATING_MODEL.md` §9 참고) |
| **R2** — schema/architecture contract | 공유 schema, cross-language interface, ADR | plan 먼저, 호환성 검증 필수, breaking change는 별도 승인 |
| **R3** — runtime trading code | Java OMS, Risk Gateway, Execution | plan 먼저, 도메인별 테스트 필수, second AI reviewer 필요 |
| **Python backtest** (`python/**`) | 백테스트/research 코드 | plan 먼저, Python tests + `docs/06_VALIDATION_POLICY.md` §4 편향 체크리스트 확인. second AI reviewer는 요구하지 않음(`docs/claude/CODERABBIT_REVIEW_MODEL.md` §3과 일치) |
| **R4** — live/risk/credential/deployment | live flag, 실제 거래소 write, leverage, risk limit, key, production 배포 | **기본 BLOCKED / plan-only.** 명시적 human decision 없이 구현 금지. 현재 단계에서는 live enablement 자체가 금지 |

분류가 모호하면 **더 높은 risk class**를 적용한다.

---

## D. Standard lifecycle

1. source docs/code 읽기
2. risk class 지정
3. plan
4. dedicated worktree/branch
5. small scoped implementation
6. local validation
7. self-review
8. commit
9. push
10. PR 생성
11. `security-gates`
12. CodeRabbit review
13. 유효한 finding만 수정
14. latest-head review completeness 확인(`docs/claude/CODERABBIT_REVIEW_MODEL.md` §9)
15. human final merge action
16. handoff 갱신(`docs/claude/PM_HANDOFF.md`, 필요 시)

> 14번 항목(latest-head review completeness)은 구현한 세션 자신의 self-check
> 외에, 독립적인 fresh-context subagent(`pr-auditor`, 아래 F.1 표와
> `docs/claude/CLAUDE_OPERATING_MODEL.md` §13 참고)로도 수행할 수 있다.
> `pr-auditor`는 구현 세션과 대화 맥락을 전혀 공유하지 않고 GitHub 상태를
> 스스로 다시 조회해 `docs/claude/CODERABBIT_REVIEW_MODEL.md` §9 체크리스트를
> 판정하며, 판정을 PR에 댓글로 직접 게시한다(`/audit-pr <PR번호>`). merge
> 권한은 없다 — 여전히 15번(human final merge action)이 최종 결정이다.

---

## E. Hybrid rule

- 1 PR = Python only / Java only / schema only.
- Python/Java 동시 변경은 schema migration 등 **명확한 계약 변경**에만 허용한다.

---

## F. Agents/hooks status

현재(v4 기준) 실제 구현 상태:

| 항목 | 상태 |
|---|---|
| Project subagents | **구현됨** (`.claude/agents/*.md`, 5개 read-only domain reviewer + 1개 read-only-files/read-only-GitHub `pr-auditor`, 아래 F.1 참고) |
| Skills / slash commands | **구현됨** (`.claude/skills/review-*/SKILL.md` 5개 + `.claude/skills/audit-pr/SKILL.md` 1개, 전부 manual-only, 아래 F.2 참고) |
| Hooks / permissions | **구현됨** (`.claude/settings.json` deny rules + PreToolUse policy guard hook, 아래 F.2 참고) |
| Implementation agents | 미구현 |
| `CodeRabbit` required check | 구현됨 (PR #1) |
| `security-gates` required check | 구현됨 (PR #2) |

v3에 있던 subagent/hooks 추천 목록은 **"추천"이지 "현재 사용 가능"이 아니다.**
이번 PR(#5)까지 project subagent 5개(read-only reviewer), 그 5개를 감싸는
manual-only skill 5개, 그리고 project 수준 deny permission + PreToolUse policy
hook이 구현되었다. implementer agent는 여전히 미구현이며 후속 PR에서 진행한다.
진행 시 이 표와 `docs/claude/PM_HANDOFF.md`를 함께 갱신한다.

### F.1 Implemented project reviewer subagents

다음 5개는 `.claude/agents/*.md`에 정의된 **read-only reviewer** subagent다.
모두 `tools: Read, Grep, Glob`, `model: inherit`만 사용하며 파일 수정, shell
실행, nested agent 생성, MCP 사용이 tool pool에 아예 존재하지 않는다. 구현자
agent가 아니며, CodeRabbit/`security-gates`를 대체하지 않는다. 최종 merge
authority도 아니다.

| 변경 유형 | 기본 reviewer |
|---|---|
| architecture / ADR / schema contract | `architecture-reviewer` |
| Java OMS / state lifecycle | `java-oms-reviewer` + `test-reviewer` |
| Python research / backtest | `python-research-reviewer` + `test-reviewer` |
| risk / R4-adjacent | `risk-reviewer` |
| cross-language / high-risk runtime 변경 | `architecture-reviewer` + 해당 domain reviewer + second AI reviewer policy(`docs/claude/CLAUDE_OPERATING_MODEL.md` §9) |
| PR merge-readiness 독립 감사 (§9 latest-head review completeness) | `pr-auditor` |

`pr-auditor`는 위 5개와 성격이 다르다 — 코드 품질이 아니라 "이 PR을 지금
merge해도 되는가"만 판정하며, 그 판정에 필요한 정보(CI 상태, CodeRabbit
review 이력, unresolved thread)가 로컬 파일이 아니라 GitHub에만 존재하기
때문에 `Bash`(읽기 전용 `gh`/`git` 명령 한정 + 판정 댓글 게시 1건만 예외)가
추가로 필요하다. 상세 tool boundary와 5개 reviewer와의 차이는
`docs/claude/CLAUDE_OPERATING_MODEL.md` §13을 본다.

### F.2 Implemented project skills and permission/hook guardrails (PR #5)

F.1의 5개 reviewer subagent를 **사용자가 직접 호출**할 수 있도록 감싼 5개
manual-only project skill이 `.claude/skills/review-*/SKILL.md`에 구현되어
있다. 모두 `disable-model-invocation: true`, `context: fork`이며, 대응하는
`agent`를 명시적으로 지정한다 — 즉 model이 자동으로 호출할 수 없고, 항상
`/review-*` 형태로 사용자가 직접 호출해야 하며, 호출 시 격리된 fork 세션에서
실행된다.

| 명령 | 감싸는 reviewer agent |
|---|---|
| `/review-architecture` | `architecture-reviewer` |
| `/review-oms` | `java-oms-reviewer` |
| `/review-backtest` | `python-research-reviewer` |
| `/review-risk` | `risk-reviewer` |
| `/review-tests` | `test-reviewer` |
| `/audit-pr <PR번호>` | `pr-auditor` |

이 skill들은 side-effect가 없는 **wrapper**다. 파일을 만들거나 수정하지 않고,
새 권한을 부여하지 않으며, 감싸는 reviewer agent의 tool boundary를 그대로
상속한다. `allowed-tools` 사전 승인, skill-scoped hook, `!` shell injection은
사용하지 않는다. 이 저장소는 `.claude/commands/` 대신 이 skill 형식을
사용한다(현재 Claude Code 버전에서 두 형식이 동등하게 동작하므로, project
custom command는 전부 skill로 구현한다).

`/audit-pr`만 예외가 하나 있다: 감싸는 `pr-auditor`가 판정을 GitHub PR에
댓글로 게시하는 것 자체가 이 skill의 목적이므로, "파일을 만들거나 수정하지
않는다"는 유지되지만 "GitHub에 아무 흔적을 남기지 않는다"는 유지되지 않는다
— 그 유일한 쓰기 동작(`gh pr comment`)은 `pr-auditor` 자체 정의(§F.1)에
명시적으로 한정되어 있다.

같은 PR에서 `.claude/settings.json`에 project 수준 **deny-only permission
규칙**과 단일 **PreToolUse policy guard hook**(`.claude/hooks/policy_guard.py`,
표준 라이브러리만 사용)이 추가되었다. 이 hook은 `Bash|Edit|Write|MultiEdit|NotebookEdit|Read|Grep` 호출마다
실행되며, secret/private 경로 읽기/쓰기, live trading flag 활성화 문자열, 그리고
일부 위험한 Bash 패턴(main 직접 push, force push, `gh pr merge`, branch
protection mutation, `rm -rf`, `docker --privileged`, BingX live order
endpoint 등)을 결정론적으로 차단한다. 상세 위협 모델과 한계는
`docs/claude/CLAUDE_OPERATING_MODEL.md` §14를 본다.

이 permission deny + hook 계층은 **pre-execution** 층이며, `CodeRabbit`/
`security-gates`(pre-merge 층)를 대체하지 않는다. 서로 보완하는 별개의 층이다.
hook은 완전한 shell parser가 아니며 모든 우회를 잡는다고 주장하지 않는다 —
알려진 한계는 `docs/claude/PM_HANDOFF.md` §4를 본다.
