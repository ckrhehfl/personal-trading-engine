# PM Handoff

**Status:** supporting reference / current snapshot (living document, not source of truth)
**Last verified main SHA:** `801de65a20b5765b0e78f4441eb9309a11bcebb7`
**Repository visibility:** public
**Current phase:** architecture / governance / CI foundation (no product code yet)

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
- project subagents 미구현
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

1. (이번 PR) Claude operating model / PM handoff
2. Project subagents
3. Skills / slash commands / hooks / permissions
4. Shared schema skeleton
5. Java OMS state machine skeleton
6. Java Risk Gateway skeleton
7. Python deterministic backtest skeleton
8. Schema compatibility tests
9. Paper broker

---

## 7. Next task packet (Project subagents)

실제 subagent 파일은 이번 PR에서 만들지 않는다. 다음 작업을 위한 준비된
task packet:

- **Goal:** 저장소에서 반복적으로 필요한 역할(quant-researcher,
  backtest-engineer, java-oms-engineer, risk-manager, execution-engineer,
  security-reviewer, test-engineer, docs-pm 등, `docs/09_CLAUDE_WORKFLOW.md`
  v3의 추천 목록 재검토 후 확정)을 project-level subagent로 정의한다.
- **In scope:** `.claude/agents/*.md` 정의 파일 생성, 각 agent의 tool 범위와
  risk class 매핑 문서화.
- **Out of scope:** hooks, permissions, 실제 제품 코드, skill/slash command
  구현.
- **Source docs:** `docs/09_CLAUDE_WORKFLOW.md`, `docs/claude/CLAUDE_OPERATING_MODEL.md`,
  이 문서.
- **Risk class:** R1 (repo/tooling/governance).
- **Acceptance criteria:** 각 subagent가 명시적 역할/도구 범위/risk class를
  갖고, `docs/09_CLAUDE_WORKFLOW.md` §F 표가 "미구현"에서 "구현됨"으로 갱신됨.
- **Validation:** 문서 링크 무결성, 기존 gate(CodeRabbit/security-gates) 통과.
- **Required review:** CodeRabbit + security-gates. Second reviewer는 불필요
  (문서/설정 범위, R1).
- **Stop conditions:** subagent 정의가 CLAUDE.md의 비협상 규칙을 완화하려는
  경우, 또는 R4 범위(live/risk/credential) 작업을 자동 승인하는 방향으로
  설계되는 경우.

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
