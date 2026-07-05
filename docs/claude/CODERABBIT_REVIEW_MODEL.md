# CodeRabbit 리뷰 모델 & Merge Gate

이 문서는 **코드를 깊게 읽지 않고도 안전하게 merge 여부를 판단**하기 위한 규칙이다.

전제: 저장소 소유자는 코드 세부를 직접 판별하지 않을 수 있다. 그래서 "사람의 코드
판단"에 의존하는 대신, **기계적 merge gate(mechanical gate)**로 안전을 보완한다.
사람은 최종 버튼을 누르지만, 코드가 맞는지 틀리는지를 혼자 판정하지 않는다.

관련 파일: `.coderabbit.yaml`(리뷰 설정), `docs/00_INDEX.md`(문서 기준),
`../../CLAUDE.md`(최상위 안전 규칙).

---

## 1. 역할 분담

| 주체 | 역할 | 하지 않는 것 |
|---|---|---|
| **Claude Code** | 구현자/문서 작성자. 변경을 만들고 self-review 후 PR 생성 | 스스로 merge 승인하지 않음 |
| **CodeRabbit** | 1차 독립 AI 리뷰어. 위험/버그/정책 위반 지적, pre-merge check 수행 | 유일한 최종 판정자가 아님 |
| **GitHub required checks** | 기계적 merge gate. 통과 못 하면 merge 불가 | 판단하지 않고 규칙만 강제 |
| **Human(사용자)** | 최종 merge 버튼. 아래 체크리스트만 확인 | 코드 라인 단위 정합성의 단독 판정자는 아님 |

핵심: **"사람이 코드를 이해했는가"가 아니라 "gate가 전부 초록인가"로 merge를 결정한다.**

---

## 2. 기본 Merge Gate (모든 PR 공통)

아래가 **전부 충족**될 때만 merge한다. 하나라도 아니면 merge 금지.

- [ ] 변경은 PR을 통해서만 들어온다. `main` 직접 push 금지.
- [ ] 모든 required check가 green (`CodeRabbit` + `security-gates`).
- [ ] CodeRabbit 리뷰가 **완료**됨(pending 아님).
- [ ] 실패한 pre-merge custom check가 **0건**.
- [ ] 해결되지 않은(unresolved) 리뷰 conversation이 **0건**.
- [ ] **현재 head SHA 기준**으로 9절 Latest-Head Review Completeness 조건을 모두 확인.
- [ ] PR description이 존재하고 아래를 명시:
  - 변경 파일
  - 추가/변경한 테스트
  - 테스트 실행 결과
  - 남은 위험
  - human approval 필요 여부

---

## 3. 위험도별 추가 Gate

낮은 위험부터 높은 위험 순.

| 변경 유형 | 기본 gate | 추가 gate |
|---|---|---|
| **docs only** | ✅ | 없음(기본 gate로 충분) |
| **Claude 설정 / agents / skills / hooks** | ✅ | 변경 파일 목록을 명시적으로 확인(의도치 않은 파일 포함 여부) |
| **schema (`schemas/**`)** | ✅ | schema validation 통과 + Python/Java 양쪽 호환 확인 |
| **Python backtest (`python/**`)** | ✅ | Python tests 통과 + `docs/06_VALIDATION_POLICY.md` §4 편향 체크리스트 확인 |
| **Java OMS/Risk/Execution (`java/**`)** | ✅ | Java tests 통과 + **두 번째 AI 리뷰** |
| **live / API key / leverage / risk 완화** | — | **현재 단계에서는 merge 금지** (아래 5절) |

---

## 4. 사람이 확인할 최소 체크리스트

코드를 이해하지 못해도 확인 가능한 항목만 담았다. 하나라도 "예"이면 **merge 보류**.

1. PR 제목/설명에 `live`, `leverage 상향`, `risk 완화`, `secret`, `API key` 중 어떤
   것이라도 "활성화/상향/완화"한다는 내용이 있는가?
2. CodeRabbit이 남긴 리뷰 중 **해결되지 않은** 지적이 있는가?
3. 실패(빨강)한 check가 하나라도 있는가?
4. PR 설명에 "테스트를 실행하지 않았다" 또는 테스트 결과가 **비어 있는가**?
5. `docs/00_INDEX.md`의 `결정 필요` 항목을 이 PR이 **임의로 확정**했는가?
6. 변경 파일 목록에 `.env`, `secrets/`, `credentials/`, 실제 key 파일이 있는가?

모두 "아니오" 이고 2절 기본 gate가 초록이면 merge 후보다.

---

## 5. Merge 절대 금지 조건 (Hard Block)

아래 중 **하나라도** 해당하면 점수·리뷰와 무관하게 merge 금지.

- `.env` 또는 실제 credential/secret/token/password/private key 포함
- `secrets/` 또는 `credentials/` 경로의 실제 자격증명 추가
- live trading 활성화 (`LIVE_TRADING_ENABLED=true`, `live_trading=true` 등)
- leverage 한도 상향
- risk / loss limit 완화
- market order guard 완화
- Python이 거래소에 직접 live order를 보내는 경로
- Java Risk Gateway 우회 경로
- 실패(빨강)한 check 존재
- 해결되지 않은 blocker 존재
- 테스트를 실행하지 않음(결과 없음)
- `결정 필요` 항목을 근거 없이 확정

이 항목들은 human approval이 있어도 **본 단계(MVP/파이프라인 준비 단계)에서는**
기본적으로 merge하지 않는다. 실제 완화가 필요하면 별도 정책 PR과 명시적 승인 절차를
먼저 만든다.

---

## 6. CodeRabbit은 완벽하지 않다

- 단일 AI 리뷰어에만 의존하지 않는다. CodeRabbit은 놓칠 수 있다.
- **고위험 PR**(Java OMS/Risk/Execution, schema, live 관련)은 **두 번째 AI 리뷰**를
  추가로 받는다(예: 별도 Claude Code review 세션 또는 다른 reviewer).
- 실제 live 단계로 가면 이 문서의 gate를 더 강화한다(수동 검증, 별도 승인자 등).

---

## 7. 현재 단계에서 아직 하지 않는 것

- auto-merge
- unattended merge(사람 확인 없는 자동 병합)
- live deployment
- risk / leverage 자동 승인

이것들은 별도의 정책 문서와 명시적 승인 절차가 생긴 뒤에만 검토한다.

---

## 8. Deterministic Security Gates (`security-gates`)

CodeRabbit(1절)과 별개로, `.github/workflows/security-gates.yml` +
`scripts/ci/security_gates.py` 가 **AI 판단 없이 기계적으로** 실패 조건을 막는다.

| | CodeRabbit | security-gates |
|---|---|---|
| 성격 | AI 리뷰어. 맥락을 읽고 판단 | 결정론적 스크립트. 패턴 매치만 수행 |
| 놓칠 수 있음 | 예 (2·6절) | 예 — 아래 커버 범위 밖은 못 잡음 |
| 대체 관계 | CodeRabbit이 security-gates를 대체하지 않음 | security-gates가 CodeRabbit을 대체하지 않음 |

**둘 다 required check로 green이어야 merge 후보다.** 하나만 통과해서는 안 된다.

### 8.1 현재 구현된 gate

| Gate | 대상 | 확인 |
|---|---|---|
| `FORBIDDEN_TRACKED_PATH` | HEAD의 `git ls-files` | `.env`(`.env.example` 제외), `secrets/`·`credentials/`·`private/`, `*.pem/*.key/*.p12/*.pfx/*.kdbx`, `id_rsa`/`id_ed25519`, `.gitignore`의 export 패턴과 일치하는 tracked path |
| `LIVE_TRADING_ENABLEMENT` | PR로 추가된 라인(docs/`*.md`/`.coderabbit.yaml` 제외) | `LIVE_TRADING_ENABLED`/`live_trading` 이 true 로 설정 |
| `PYTHON_DIRECT_LIVE_ORDER_PATH` | `python/**` 에 추가된 라인 | BingX swap 실주문/batch order endpoint 문자열의 직접 참조 |
| `RISK_POLICY_CHANGE_BLOCKED` | 변경된 파일 목록 | `docs/05_RISK_POLICY.md` 자체의 변경을 임시로 전면 차단(아래 8.2) |
| `DANGEROUS_PIPE_INSTALL` | 추가된 라인(docs 제외) | `curl`/`wget` 출력을 `sh`/`bash` 로 직접 파이프 |
| `UNSAFE_WORKFLOW_TRIGGER` / `WORKFLOW_WRITE_PERMISSION` / `WORKFLOW_SECRET_REFERENCE` / `UNPINNED_ACTION` | HEAD의 `.github/workflows/*.yml(.yaml)` 전체 | `pull_request_target`/`workflow_run`, write 권한, `${{ secrets. }}` 참조, full 40자 SHA로 pin 되지 않은 external action |

### 8.2 알려진 한계 (임시 조치, 확대 필요)

- **Risk policy freeze는 임시다.** 아직 machine-readable risk config/schema가 없어서
  숫자형 semantic comparison(leverage/loss limit 실제 값 비교)을 구현할 수 없다.
  현재는 `docs/05_RISK_POLICY.md` 변경 자체를 막는 방식으로 대신한다. 이 문서를
  실제로 갱신해야 하면 별도 정책 PR + 명시적 human approval 절차를 먼저 만든다.
- **Python direct live-order 탐지는 알려진 BingX swap order endpoint 문자열만
  잡는다.** 모든 미래 SDK 호출 경로를 막는다고 보장하지 않으며, BingX adapter가
  실제로 구현되는 단계에서 gate를 확장해야 한다.
- GitHub의 built-in secret scanning / push protection은 이 gate와 **별도 계층**이며,
  이 gate가 그 기능을 대체하지 않는다.
- 이 gate는 GitHub Actions `pull_request` 트리거의 base/head SHA만 정확히 diff한다.
  `workflow_dispatch` 수동 실행 시에는 diff 대상이 없어 정적 gate(`FORBIDDEN_TRACKED_PATH`,
  workflow hardening)만 사실상 유효하다.

### 8.3 여전히 금지

- `security-gates` 를 우회하거나 약화하는 변경.
- 실패하는 gate를 삭제/skip 처리해서 통과시키는 것.
- auto-merge — 8절 이전의 원칙(7절)과 동일하게 이 gate 추가 이후에도 여전히 금지.

---

## 9. Latest-Head Review Completeness

**교훈(PR #2):** required check green(`CodeRabbit` + `security-gates`)과
CodeRabbit formal `APPROVED`만으로는 merge readiness 판정에 **불충분했다.**
fix commit을 push한 뒤, 같은 head SHA에 대해 formal `APPROVED` review와는
**별도로** `COMMENTED` review가 존재했고, 그 안에 outside-diff Critical
finding이 남아 있었다. GitHub PR UI/`reviewDecision` 필드만 보면 이 finding을
놓칠 수 있다.

이 문제를 재발시키지 않기 위해, **`READY_TO_MERGE` 판정은 항상 "현재 head
SHA"에 대한 판정**이며, 다음을 **모두** 확인해야 한다.

- [ ] 판정 대상 head SHA를 명시적으로 확인했다.
- [ ] 새 commit이 push되면 **이전의 모든 readiness 판정은 즉시 무효**다.
      head SHA를 기준으로 처음부터 다시 확인한다.
- [ ] required checks(`CodeRabbit`, `security-gates`)가 green이다.
- [ ] CodeRabbit **formal `APPROVED`**다(`reviewDecision`).
- [ ] **review submissions 전체**를 봤다 — `APPROVED` 하나만 보고 끝내지
      않는다. 같은 head 또는 이전 head에 남은 `COMMENTED`/`CHANGES_REQUESTED`
      review가 있는지 확인한다.
- [ ] `COMMENTED` review의 본문과 **outside-diff 코멘트**(diff 라인에 걸리지
      않아 inline으로 안 붙는 코멘트)를 확인했다.
- [ ] top-level(issue-level) 코멘트에 새 blocker가 없다.
- [ ] unresolved inline review conversation이 **0건**이다.
- [ ] latest head 기준으로 actionable Critical/Major finding이 **0건**이다.
- [ ] PR이 `mergeable` 상태다.

위 전부가 "예"일 때만 `READY_TO_MERGE`로 보고한다. 하나라도 확인하지 못했거나
"아니오"이면 `REVIEW_PENDING` 또는 `BLOCKED`로 보고한다.
