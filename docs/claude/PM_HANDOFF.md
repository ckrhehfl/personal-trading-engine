# PM Handoff

**Status:** supporting reference / current snapshot (living document, not source of truth)
**Last verified base main SHA:** `d685426d63a9ae4b52eb1ccf362e79b7090365f0`
**Repository visibility:** public
**Current phase:** architecture / governance / CI / Claude reviewer-agent + skill/guardrail foundation (no product code yet)

> **Source-of-truth warning:** 이 문서는 "지금 상태가 무엇인지" 요약하는
> 스냅샷이다. 제품/리스크/아키텍처 결정을 여기서 새로 확정하지 않는다. 값이
> 각 주제의 source-of-truth 문서(`docs/00_INDEX.md` §3)와 다르면 **그 문서가
> 옳다.**

---

## 1. Project state

**Completed:**

- Hybrid-lite architecture 방향 확정 (D007, `docs/03_HYBRID_JAVA_ARCHITECTURE_DECISION.md`)
- PR #1 — governance bootstrap (`.coderabbit.yaml`, 문서 registry, merge-gate 모델)
- Public 전환 (private → public)
- `main` branch protection 적용
- Secret scanning 활성화
- Push protection 활성화
- PR #2 — deterministic security gates (`security-gates` required check)
- PR #3 — Claude operating model / PM handoff 문서화
- PR #4 — project reviewer subagents 5개 (`architecture-reviewer`,
  `java-oms-reviewer`, `python-research-reviewer`, `risk-reviewer`,
  `test-reviewer`), 전부 `.claude/agents/*.md`, read-only
  (`tools: Read, Grep, Glob`)
- PR #5 — project skills 5개(`.claude/skills/review-*/SKILL.md`, PR #4의
  reviewer subagent 5개를 감싸는 manual-only wrapper, `disable-model-invocation:
  true` + `context: fork`), project 수준 deny-only permission rule과
  PreToolUse policy guard hook(`.claude/settings.json`,
  `.claude/hooks/policy_guard.py`, 표준 라이브러리만 사용), 지원 테스트
  (`tests/claude/test_policy_guard.py`)

**현재 required checks** (GitHub API 실측, `branches/main/protection`):

- `CodeRabbit`
- `security-gates`

**현재 merge protection** (GitHub API 실측):

- PR 필수 (main 직접 push 불가)
- strict status checks: `true`
- enforce admins: `true`
- required approving review count: `1`
- dismiss stale reviews: `true`
- required conversation resolution: `true`
- allow force pushes: `false`
- allow deletions: `false`
- required linear history: `true`

**현재 security posture** (GitHub API 실측, `security_and_analysis`):

- `secret_scanning`: enabled
- `secret_scanning_push_protection`: enabled
- `secret_scanning_validity_checks`: disabled
- `secret_scanning_non_provider_patterns`: disabled
- `dependabot_security_updates`: disabled

---

## 2. Current code maturity

**있음:**

- architecture/docs foundation (`docs/00_MASTER_SUMMARY.md` 외 00~12)
- governance/security foundation (`CLAUDE.md`, `.coderabbit.yaml`, `security-gates`)

**아직 없음:**

- shared schema 구현
- Java OMS 구현
- Risk Gateway 구현
- Python backtest 구현
- BingX adapter
- paper trading
- live trading

---

## 3. Important operating lessons

**PR #1:** CodeRabbit의 stale change-request 상태는 PR 코멘트에
`@coderabbitai approve`로 정리할 수 있었다.

**PR #2:** formal `APPROVED` + required checks green만으로는 merge readiness
판정에 **불충분**했다. fix commit 이후 별도 `COMMENTED` review에 outside-diff
Critical finding이 formal approval과 **동시에** 존재했다. 이후 latest-head
review completeness 확인(`docs/claude/CODERABBIT_REVIEW_MODEL.md` §9)을
도입했다.

---

## 4. Known limitations / technical debt

- `docs/05_RISK_POLICY.md`는 임시로 전체 변경이 차단됨(machine-readable risk
  config가 아직 없음)
- 숫자형 risk relaxation semantic comparison 없음
- Python 직접 주문 탐지기는 알려진 BingX endpoint 문자열만 커버(좁은 범위)
- `security-gates`의 워크플로우 하드닝 검사는 전체 YAML 파서가 아닌 line 기반
- project reviewer subagent는 read-only reviewer 5개뿐이다 — implementation
  agent(구현자)는 아직 없음
- 언제 어떤 reviewer subagent를 호출해야 하는지에 대한 자동 강제(enforcement)가
  없다 — `docs/09_CLAUDE_WORKFLOW.md` §F.1 delegation table은 가이드일 뿐,
  자동으로 강제되지 않는다
- second AI reviewer 자동화 미구현(문서상 요구사항만 존재)
- (PR #5) `.claude/hooks/policy_guard.py`는 high-confidence pattern matcher이며
  완전한 shell parser가 아니다 — 의도적으로 난독화된 명령은 통과할 수 있다
- (PR #5) 간접 subprocess 호출이나 hook이 보지 못하는 경로를 통한 파일 접근은
  model-level 규칙(deny rule/hook)을 우회할 수 있다
- (PR #5) `.claude/settings.json`/`.claude/hooks/policy_guard.py` 자체의
  무결성은 아직 `security-gates`가 별도로 고정(freeze)하지 않는다 — 이 파일들이
  후속 PR에서 약화되는 것을 막는 기계적 장치는 아직 없다
- (PR #5) implementation agent는 여전히 없다 — 새 skill 5개는 모두 read-only
  reviewer의 manual-only wrapper일 뿐이다
- (PR #5, CodeRabbit finding 반영) PreToolUse hook matcher는
  `Bash|Edit|Write|MultiEdit|NotebookEdit|Read`로 확장되었다 —
  `MultiEdit`/`NotebookEdit`도 hook의 내용 검사(secret path, live trading
  flag) 대상이 되었고, `Read`도 secret path 열람 차단 대상이 되었다(CodeRabbit
  Critical finding: `.env.local`/`.env.production`/`id_rsa`/`id_ed25519`/
  `*.kdbx` 등이 Read 경로에서 빠져 있던 문제)
- (PR #5) `.claude/settings.json`의 `Read(.env.*)` deny 규칙은 `.env.example`
  읽기도 함께 차단한다 — `permissions.allow` 예외 규칙 추가가 이 PR에서
  금지되어 있어 세밀한 예외를 둘 수 없었고, live/secret 탐지에서는 과차단이
  누락보다 안전하다는 원칙에 따라 의도적으로 보수적으로 설계했다
- (PR #5) `context: fork` + `agent:` 조합이 감싸는 reviewer subagent의
  `tools: Read, Grep, Glob` 제약을 forked 실행 컨텍스트에 구조적으로
  강제하는지는 실제 라이브 세션에서 별도로 검증되지 않았다 — 문서상 동작
  방식으로는 그렇게 되어야 하지만, 이 PR에서 실제 `/review-*` 호출로
  경험적으로 확인하지는 않았다
- (PR #5) hook timeout(5초) 또는 `python3` spawn 실패 시 Claude Code
  harness가 fail-open(도구 진행 허용)으로 처리하는지 fail-closed(차단)로
  처리하는지 이 PR에서 직접 확인하지 못했다 — harness 레벨 동작이며
  `policy_guard.py` 자체의 범위 밖이다
- (PR #5) `git push` 앞에 global option이 오는 경우(예: `git -C path push
  origin main`)는 hook의 `_check_git_push`가 인식하지 못한다. main 직접
  push는 GitHub branch protection(서버 측)이 최종적으로 막는다
- (PR #5) `.claude/settings.json`의 정적 deny 규칙 중 main 직접 push
  항목은 `origin` remote 이름에 고정되어 있다(다른 remote 이름을 쓰면 정적
  규칙은 매치하지 않음). hook 쪽의 `_check_git_push`는 remote 이름과 무관하게
  동작하므로 이 부분은 hook이 보완한다

---

## 5. Decision required

새로운 값을 여기서 만들지 않는다. 현재 미확정 항목의 source of truth는
`docs/00_INDEX.md` §6과 `docs/10_OPEN_QUESTIONS_AND_RISKS.md`다. 요약(참고용):

1. BingX 정확한 상품 코드 / API symbol
2. Position mode — hedge vs one-way
3. Margin mode — isolated(추천) vs cross
4. 초기 주문 정책 세부 — limit-first + guarded market(추천)이나 세부 미확정
5. 손절/익절 방식 — 거래소 native stop vs 내부 risk stop
6. Python↔Java 공유 스키마 형식 — JSON Schema(v0.1 추천) vs Protobuf(후기)
7. Java strategy runtime 범위
8. 백테스트 ↔ Java live path 일치성 테스트 방법
9. VPS 위치 / 네트워크 지연 측정 기준
10. 알림 채널 — Telegram(추천) / Discord / Email

---

## 6. Next recommended sequence

번호는 논리적 순서이며 실제 GitHub PR 번호를 미리 단정하지 않는다.

1. Claude operating model / PM handoff (완료, PR #3)
2. Project reviewer subagents (완료, PR #4)
3. Project skills / permission deny rules / PreToolUse policy hook (완료, PR #5)
4. (다음 작업) Shared schema skeleton
5. Java OMS state machine skeleton
6. Java Risk Gateway skeleton
7. Python deterministic backtest skeleton
8. Schema compatibility tests
9. Paper broker

---

## 7. Next task packet (Shared schema skeleton)

이번 PR(#5)에서 project skills 5개, project 수준 deny permission rule,
PreToolUse policy guard hook이 이미 구현되었다(§1 참고). 다음 작업을 위한
준비된 task packet이며, **이번 PR에서 구현하지 않는다**:

- **Goal:** Python↔Java 공유 schema의 최소 skeleton을 만든다(형식 자체는 아직
  미확정 — 아래 참고).
- **In scope:** schema 형식 결정을 위한 설계 검토, 최소 schema 후보(예:
  OrderIntent) skeleton, schema validation 방법 검토.
- **Out of scope:** implementation agent(구현자 subagent), Java/Python 실제
  런타임 코드, live/risk 관련 설정.
- **Source docs:** `docs/00_INDEX.md` §6 (item 6), `docs/10_OPEN_QUESTIONS_AND_RISKS.md`
  §1 (item 5), `docs/11_DECISION_LOG.md`, `docs/03_HYBRID_JAVA_ARCHITECTURE_DECISION.md`,
  이 문서.
- **Risk class:** R2 (schema/architecture contract).
- **Acceptance criteria:** TBD — 실제 task packet 작성 시 확정.
- **Required review:** CodeRabbit + security-gates + `architecture-reviewer`
  (schema/contract 변경이므로 second AI reviewer 후보,
  `docs/claude/CLAUDE_OPERATING_MODEL.md` §9 참고).
- **Stop conditions:** `docs/00_INDEX.md` §6 item 6(JSON Schema vs Protobuf)을
  임의로 확정하려는 경우, 또는 CLAUDE.md의 비협상 규칙을 완화하려는 설계로
  이어지는 경우.

새 schema 형식 결정(JSON Schema vs Protobuf)은 여기서 확정하지 않는다.
`docs/00_INDEX.md` §6 item 6과 `docs/10_OPEN_QUESTIONS_AND_RISKS.md`의
"JSON Schema(v0.1 추천) vs Protobuf(후기)"를 그대로 인계한다.

---

## 8. Handoff update rule

**업데이트 시점:**

- 의미 있는 PR이 merge된 후
- major decision이 확정된 후(`docs/11_DECISION_LOG.md`에 기록된 후)
- blocker/phase가 바뀐 후

**금지:**

- source-of-truth 결정을 복제하거나 독자적으로 변경
- secret/실제 값 기록
- live credential 기록
- raw private operation data 기록
