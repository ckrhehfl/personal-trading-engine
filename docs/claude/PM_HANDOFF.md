# PM Handoff

**Status:** supporting reference / current snapshot (living document, not source of truth)
**Last verified base main SHA:** `717c2490b260b05d56d25e4c44cb812867499956`
**Repository visibility:** public
**Current phase:** architecture / governance / CI / Claude reviewer-agent foundation (no product code yet)

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
- skills / slash commands / hooks / permissions 미구현
- second AI reviewer 자동화 미구현(문서상 요구사항만 존재)

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
2. (이번 PR) Project reviewer subagents
3. Skills / slash commands / hooks / permissions
4. Shared schema skeleton
5. Java OMS state machine skeleton
6. Java Risk Gateway skeleton
7. Python deterministic backtest skeleton
8. Schema compatibility tests
9. Paper broker

---

## 7. Next task packet (Skills / slash commands / hooks / permissions)

이번 PR(#4)에서 project reviewer subagent 5개는 이미 구현되었다
(`.claude/agents/*.md`, §1 참고). 다음 작업을 위한 준비된 task packet이며,
**이번 PR에서 구현하지 않는다**:

- **Goal:** skills/slash command, hooks, permissions 설계를 검토하고 필요한
  최소 범위만 구현한다.
- **In scope:** 설계 검토, 최소 skill/slash command 후보 선정, hooks/permissions
  필요성 평가.
- **Out of scope:** implementation agent(구현자 subagent), 실제 제품 코드,
  shared schema.
- **Source docs:** `docs/09_CLAUDE_WORKFLOW.md`, `docs/claude/CLAUDE_OPERATING_MODEL.md`,
  이 문서.
- **Risk class:** R1 (repo/tooling/governance).
- **Acceptance criteria:** TBD — 실제 task packet 작성 시 확정.
- **Required review:** CodeRabbit + security-gates. Second reviewer 필요 여부는
  실제 범위 확정 후 재평가.
- **Stop conditions:** CLAUDE.md의 비협상 규칙을 완화하려는 설계, 또는 R4
  범위(live/risk/credential) 작업을 자동화하려는 방향으로 설계되는 경우.

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
