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
- [ ] 모든 required check가 green.
- [ ] CodeRabbit 리뷰가 **완료**됨(pending 아님).
- [ ] 실패한 pre-merge custom check가 **0건**.
- [ ] 해결되지 않은(unresolved) 리뷰 conversation이 **0건**.
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
| **Python backtest (`python/**`)** | ✅ | Python tests 통과 + bias 체크리스트(4절) 확인 |
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
