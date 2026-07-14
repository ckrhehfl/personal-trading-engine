# PM Handoff

**Status:** supporting reference / current snapshot — source of truth 아님
**Verification date:** 2026-07-14
**Verified main SHA:** `7ce20fe7dbdd2c996abe248a1945a6aaccad603d`
**Current phase:** Post-Candidate-20 implementation checkpoint / 다음 scope 미선정
**Latest merged:** Candidate 20 / Issue #46 / PR #47
**PR #47 최종 source head:** `fd43212a77c7725505956d87782a192eb76d30b4`
**main 반영 commit:** `7ce20fe7dbdd2c996abe248a1945a6aaccad603d`
**Open PRs / Open Issues (Candidate 20 checkpoint 기준):** 0 / 0 — 본 문서를
갱신하는 이 refresh 작업 자체가 연 Issue #48과 그 PR은 이 checkpoint 서술에서
제외한다.
**Candidate 21:** 미선정 / 미시작 — Issue/branch/PR/commit/승인된 task packet
없음.

> 이 문서는 supporting reference/현재 스냅샷일 뿐이며 source of truth가
> 아니다. `CLAUDE.md`를 이 문서가 완화하거나 override할 수 없다. 구현 상태는
> `docs/04_MVP_SCOPE_AND_ROADMAP.md`, qualification 기준은
> `docs/06_VALIDATION_POLICY.md`, 미확정 결정은
> `docs/10_OPEN_QUESTIONS_AND_RISKS.md`, 확정 결정은
> `docs/11_DECISION_LOG.md`, workflow 규칙은 `docs/09_CLAUDE_WORKFLOW.md`가
> 기준이다. 이 문서와 위 문서가 다르면 위 문서가 우선한다.

---

## 1. Executive verdict

- Candidate 1–20 tranche와 M1이 모두 승인되어 `main`에 merge되었다. 초기
  governance PR #1–#5도 merge되었다.
- 병합된 PR 수를 굳이 집계하면 **26건**이다: governance PR 5개 + Candidate
  PR 20개 + M1 PR 1개 = 26개 (PR #1–#5, #6, #9, #11, #13, #15, #17, #19,
  #21, #23, #25, #27, #29, #31, #33, #35, #37, #39, #41, #43, #45, #47). 이
  숫자는 참고용 집계일 뿐 진행률이나 완성도를 의미하지 않는다.
- 옛 handoff(Candidate 7 스냅샷)가 세운 계획은 이미 완료되었고 그 이후로도
  크게 초과 진행되었다.
- 그렇다고 전체 제품 로드맵이 완료된 것은 아니다.
- **MVP v0.1**: `docs/04_MVP_SCOPE_AND_ROADMAP.md` §2A 기준 foundation 항목
  중 "JSON logs"를 제외한 전부가 IMPLEMENTED_BASELINE이다. JSON
  operational log는 NOT_IMPLEMENTED다(로깅 의존성/logger 호출이
  저장소 어디에도 없음). 이 한 항목이 남았다는 이유로 v0.1을 미완료로
  재분류하지는 않되, v0.1을 "완료"라고도 선언하지 않는다.
- **MVP v0.2**는 여전히 진행 중이며 완료가 아니다.
- 경과한(elapsed) paper qualification 기록은 존재하지 않는다.
- canary/live readiness는 어떤 형태로도 존재하지 않는다.

---

## 2. Completed work rollup

아래 8개 그룹은 Candidate 1–20/M1이 `main`에 실제로 남긴 코드/스키마/테스트
증거를 기능 영역별로 묶은 것이다. 상세 근거/경계는
`docs/04_MVP_SCOPE_AND_ROADMAP.md` §1B/§2A/§3A가 기준이며, 아래는 그
요약이다.

### A. Governance / review safety
**Candidate/PR:** PR #1–#5, M1(PR #21) · **상태:** IMPLEMENTED_BASELINE(governance/CI 인프라, 로드맵 capability 아님)
governance bootstrap, deterministic `security-gates`(`.github/workflows/security-gates.yml`, `scripts/ci/security_gates.py`), Claude operating model/handoff 정의, read-only reviewer subagent 5개(`.claude/agents/*.md`), reviewer skill 5개 + PreToolUse policy guard(`.claude/hooks/policy_guard.py`), merge-gate self-test(`tests/ci/test_security_gates.py`, `tests/claude/test_policy_guard.py`).
**경계:** defense-in-depth 계층일 뿐 OS-level sandbox가 아니다. reviewer auto-routing과 second-AI-reviewer 자동 enforcement는 아직 없다(사람이 수동으로 호출).

### B. Contracts / deployment shape
**Candidate/PR:** 1(#6) IMPLEMENTED_BASELINE, 5(#15) **PARTIAL**, 7(#19) IMPLEMENTED_BASELINE · D011, D012
`schemas/v0.1/{common,order-intent,risk-decision,deployment-manifest}.schema.json`, `tests/schemas/fixtures/**`, Java `com.ptengine.contract.json.ContractJsonCodec` + `SharedFixtureCompatibilityTest`.
**경계:** Candidate 5는 JSON-boundary fixture 호환성만 증명한다 — 코드 자체 Javadoc이 "전체 backtest ↔ Java trading-path 행위 동등성을 증명하지 않는다"고 명시하므로 PARTIAL(`docs/04_MVP_SCOPE_AND_ROADMAP.md` §1B와 동일 라벨). 그 외 공통 경계: generated model 없음, manifest generator(Python)/loader(Java) 없음, artifact existence/checksum 증명 없음, 어떤 runtime도 아직 deployment manifest를 생성/소비하지 않으며 실행 authorization도 아니다.

### C. OMS / Risk / Paper composition
**Candidate/PR:** 2(#9), 3(#11), 6(#17), 8(#23) · **상태:** IMPLEMENTED_BASELINE(narrow)
`com.ptengine.oms.{OrderState,Order,OrderRegistry,OrderTransitions}`, `com.ptengine.risk.{RiskGateway,RiskDecision,RiskRule,RiskRejectReason}`(fail-closed pure-domain aggregator), `com.ptengine.paper.PaperBroker`, `com.ptengine.integration.PaperOrderPipeline`(실제 Risk→OMS→Paper in-process composition, PASS 우회 없음).
**경계:** 장시간 구동 runtime 없음, 영속 OMS 없음, 동시성 보장 없음, production numeric risk rule(leverage/notional/exposure/loss-limit) 없음. `PaperOrderPipeline` Javadoc 자체가 "다른 호출자가 컴포넌트를 직접 호출하는 것이 불가능하다고 주장하지 않는다"고 명시.

### D. Position / reconciliation
**Candidate/PR:** 9(#25), 10(#27), 11(#29), 17(#41) · **상태:** IMPLEMENTED_BASELINE(narrow) / 11은 PARTIAL(test-only)
`com.ptengine.reconciliation.{PositionSnapshot,PositionReconciler,PaperExecutionPositionProjector,PaperExecutionPositionReconciliationCoordinator}`, `PaperOrderPositionReconciliationIntegrationTest`(Javadoc: "Integration-only proof... Adds no production code").
**경계:** 포지션 ledger 없음, position lifecycle(open→update→close/reduce/flat) 표현 없음, fill 집계(다건 fill 합산) 없음, authoritative exchange/account 원천 없음, tolerance/staleness policy 없음, continuous reconciliation service 없음.

### E. Research / reporting
**Candidate/PR:** 4(#13), 12(#31), 14(#35), 16(#39) · **상태:** IMPLEMENTED_BASELINE(narrow)
`python/ptengine/backtest/{model,engine,strategy,evaluation,report}.py`(deterministic next-bar-open engine, IS/OOS 분리, `BacktestReport`/`InSampleOutOfSampleBacktestReport`), `com.ptengine.report.{DailyPaperTradingReport,DailyPaperTradingReportGenerator}`(exact invariant 10개 포함).
**경계:** 데이터 수집/영속화 없음, 차트/대시보드 없음, report/alert delivery 없음, qualification score나 pass/fail gate 없음, scheduler 없음.

### F. Cross-language evidence
**Candidate/PR:** 13(#33) · **상태:** PARTIAL
`tests/execution/fixtures/python-java-market-fill-equivalence-v0.1.json`, `PythonJavaMarketFillEquivalenceTest.java`, `test_python_java_market_fill_equivalence.py` — 4개 고정 MARKET fill 케이스의 economic side + 정확한 execution price 일치만 증명.
**경계:** LIMIT/NO_FILL equivalence, 일반 슬리피지 모델 parity, signal parity, risk-rule parity, runtime parity는 테스트 자체 docstring이 명시적으로 범위 밖이라고 선언한다.

### G. Status reconciliation
**Candidate/PR:** 15(#37) · **상태:** IMPLEMENTED_BASELINE(governance 산출물)
`docs/04_MVP_SCOPE_AND_ROADMAP.md` §1A에 정의된 4-label(IMPLEMENTED_BASELINE/PARTIAL/NOT_IMPLEMENTED/DECISION_REQUIRED) 상태 어휘 도입, Candidate 1–14 evidence 재감사.
**경계:** 이 자체는 신규 product capability가 아니라 문서/audit 산출물이다.

### H. BingX public market data
**Candidate/PR:** 18(#43), 19(#45), 20(#47) · **상태:** PARTIAL / D013, D014, D015
`com.ptengine.bingx.market.{BingxPerpetualTrade,BingxPerpetualCandle,BingxPublicMarketDataClient,BingxPublicMarketDataException}` — `fetchRecentBtcUsdtTrades`(최근 체결), `fetchRecentBtcUsdt15mCandles`(최근 15m 캔들), `fetchBtcUsdt15mCandlesInRange`(caller-supplied `startTime`/`endTime` half-open bounded range, 15분 grid 정렬, 최대 1000 candle 폭). 전부 public unauthenticated 단발성 GET 1회.
**경계:** 자격증명/서명 없음, private/account/order API 없음, collector/storage/scheduler 없음, WebSocket 없음, retry/pagination 없음, 다른 symbol/timeframe 매핑 없음, 어떤 주문 권한도 없음.

---

## 3. Current capability matrix

**IMPLEMENTED_BASELINE**
shared contracts(schemas v0.1) · Java OMS state machine · Risk Gateway
machinery(fail-closed, numeric rule 제외) · `PaperBroker` · one-shot
`PaperOrderPipeline` · position/reconciliation primitives(`PositionSnapshot`,
`PositionReconciler`, `PaperExecutionPositionProjector`) · one-shot
`PaperExecutionPositionReconciliationCoordinator` · daily report
model/generator · Python backtest engine · Python backtest report ·
deterministic test suites 및 governance gate(`security-gates`,
policy guard self-tests)

**PARTIAL**
BingX market-data capability — public 단발성 read 정확히 3가지(recent
trades, recent 15m candles, bounded historical 15m range)만 · Python↔Java
행동 동등성 — 고정 4 MARKET 케이스만 · Java↔shared-fixture JSON-boundary
compatibility(Candidate 5, `ContractJsonCodec`) — fixture accept/reject/
round-trip 호환성만, 전체 backtest↔Java trading-path 행위 동등성 아님

**NOT_IMPLEMENTED**
structured JSON operational log · continuous market-data collector · candle
persistence/storage/retention/gap repair · pagination/backfill · WebSocket ·
Java paper runtime/scheduler/process lifecycle · 실시간 strategy signal
feed · persistent/restart-safe OMS · multi-fill/partial-fill accounting ·
position update/close/reduce/flat lifecycle · production numeric risk
rule · kill switch · alert renderer/transport · private/account/position/
order BingX API · 자격증명/서명 · order placement · paper qualification ·
canary/live runtime

**DECISION_REQUIRED (정확히 8개, `docs/10_OPEN_QUESTIONS_AND_RISKS.md` §1
item 2–9)**
2. Position mode(hedge/one-way) · 3. Margin mode(isolated 우선 검토) · 4.
초기 주문 정책 세부 · 5. 손절/익절 방식 · 6. Java strategy runtime 범위 ·
7. 백테스트↔Java trading path 일치성 검증 방법 · 8. VPS 위치/네트워크
지연 기준 · 9. 알림 채널

D011(공유 계약 형식)은 해결됨(D011). D013/D014/D015는 각각 명시된 narrow
경계(recent-trades symbol mapping / 15m kline interval·응답 매핑 /
bounded historical range semantics) 안에서만 해결됨 — BingX symbol
매핑은 더 이상 미확정 목록에 없다(옛 항목 1은 D013으로 해결, 제거됨). 이
narrow 해결을 "BingX adapter 완성"으로 확대 해석하지 않는다.

---

## 4. Does the bot work? (봇이 실제로 동작하는가)

**Component-level:** YES, within the narrow deterministic contracts that
are implemented.

**Operational bot:** NO, the repository has not demonstrated a continuous
paper or live trading bot.

설계의 부품들은 좁은 단위에서 설계대로 동작하지만, 설계 전체가 실제 운용
환경에서 연속적으로 동작한다는 증거는 아직 없다.

**증명된 것:** deterministic domain behavior · fail-closed validation ·
실제 one-shot RiskGateway-controlled pipeline composition · one-shot public
BingX read · deterministic backtest/reporting · targeted integration/parity
test.

**증명되지 않은 것:** continuous orchestration · market-data
collection/storage · 실시간 strategy signal consumption · persistent/
restart 복구 · multi-fill/position lifecycle · authoritative exchange/
account reconciliation · production numeric risk rule · kill switch ·
alert/report delivery · 경과한 paper 운영 · private/order API · canary/live
운영.

---

## 5. Reproducible audit runbook

명령/경로는 이 refresh 작성 시점에 독립적으로 존재를 확인했다. 실행 전
최신 상태에서 다시 확인할 것.

**A. Repository state (non-destructive)**
```bash
git fetch origin
git switch main
git pull --ff-only origin main
git rev-parse HEAD
git rev-parse origin/main
git status --short
git log --oneline -30
```
`git reset --hard`는 감사 절차에 포함하지 않는다.

**B. Clean Python environment**
```bash
python3 -m venv .venv-audit
. .venv-audit/bin/activate
python -m pip install --upgrade pip
python -m pip install --require-hashes -r requirements/schema-validation.txt
```
JDK 21 필요(`java/build.gradle`의 toolchain `languageVersion = JavaLanguageVersion.of(21)`).
미설정 interpreter에서 `referencing` 모듈 누락은 환경 설정 실패이지 저장소
회귀의 증거가 아니다.

**C. Full deterministic suites**
```bash
python -m unittest tests.schemas.test_schema_contracts -v
PYTHONPATH=python python -m unittest discover -s python/tests -p "test_*.py" -v
( cd java && ./gradlew test --no-daemon )
python -m unittest tests.ci.test_security_gates -v
python -m unittest tests.claude.test_policy_guard -v
```

**D. Targeted evidence (검증된 클래스/모듈명)**

Java: `PaperOrderPipelineTest`, `PaperOrderPositionReconciliationIntegrationTest`,
`PaperExecutionPositionReconciliationCoordinatorTest`,
`BingxPublicMarketDataClientTest`, `BingxPublicMarketDataClientCandleTest`,
`BingxPublicMarketDataClientRangeTest`, `PythonJavaMarketFillEquivalenceTest`.

Python: `tests.backtest.test_python_java_market_fill_equivalence`,
`tests.backtest.test_report`.

타겟 명령은 elapsed 운영을 증명하지 않는다 — unit/integration 증거일
뿐이다.

**E. Source-of-truth consistency**
code/schema를 D011–D015와 대조, 상태 라벨을 `docs/04`와 대조,
qualification 기준을 `docs/06`과 대조, open decision을 `docs/10`과 대조.
이 handoff 자체가 새 decision을 만들지 않았는지 확인.

**F. Safety**
live 비활성/live authority 없음 확인 · tracked secret 없음 확인 · Python
direct order path 없음 확인 · BingX public client에 private/account/order
endpoint 없음 확인 · risk-policy 완화 없음 확인 · latest-head
`security-gates` 확인 · CodeRabbit review 완전성 확인(`docs/claude/CODERABBIT_REVIEW_MODEL.md` §9) ·
human merge. CodeRabbit이 GitHub branch protection의 formal required
status-check context로 구성되어 있다고 이 문서는 단정하지 않는다 — 이
저장소가 요구하는 것은 latest-head 기준 `security-gates` green +
CodeRabbit review 완전성 확인 + human final merge라는 **review 절차**이며,
그 절차의 근거는 `docs/claude/CODERABBIT_REVIEW_MODEL.md`다.

**G. Operational audit — NOT YET EXECUTABLE**
uptime/process supervision, structured event/audit log, data
completeness/gap monitoring, restart 복구, idempotency/duplicate-order
증거, risk-bypass count, authoritative position reconciliation,
report/alert delivery 증거, kill-switch drill, paper metrics — 전부 아직
수행할 증거 자체가 없다.

**H. Paper qualification** (`docs/06_VALIDATION_POLICY.md` §7)
최소 30일(권장 45일), 50회 이상 거래, 치명적 크래시 0, 중복 주문 0, 포지션
불일치 0, risk bypass 0, daily report 누락 0, kill switch 정상 작동, paper
score 80점 이상.

**I. Canary/live**
paper gate 통과 + 명시적 human approval 전에는 시작할 수 없다: 수동 live
flag, IP 제한 API key, withdrawal 권한 없음, canary risk limit, rollback
target, incident runbook.

---

## 6. Known risks and technical debt

> `docs/09_CLAUDE_WORKFLOW.md` §F.2(현재 `main` 기준 line 171)는 PreToolUse
> policy guard hook의 알려진 한계를 "`docs/claude/PM_HANDOFF.md` §4"로
> 인용한다. 이번 refresh는 이 문서를 요청받은 고정 섹션 구조(§7 Required
> Document Content)로 전면 재구성했고, 그 결과 해당 내용은 이제 이
> 섹션(예전 번호 기준 §4가 아니라 여기)에 있다. 이 refresh는 정확히
> `docs/claude/PM_HANDOFF.md` 한 파일만 변경할 수 있어 `docs/09`의 인용
> 번호 자체는 여기서 고치지 못한다 — 별도의 작은 docs PR에서 그 참조를
> 갱신해야 한다. 아래 두 항목이 그 인용이 가리키던 내용이다.

- **PreToolUse hook은 완전한 shell parser가 아니다**: `.claude/hooks/policy_guard.py`는
  결정론적 패턴 매치이며 모든 우회 경로를 잡는다고 주장하지 않는다
  (`docs/09_CLAUDE_WORKFLOW.md` §F.2).
- **PreToolUse detector와 CI(`security-gates`) detector 사이 일부 parity
  gap**이 있다 — 두 layer는 서로 다른 시점(pre-execution vs pre-merge)에
  독립적으로 동작하며 하나가 다른 하나를 대체하지 않는다. reviewer
  auto-routing과 second-AI-reviewer 자동 enforcement도 아직 없다(§2 Group
  A와 동일 사실).
- D013–D015는 narrow live-observation 기반 public-read 결정이다. 벤더
  동작이 통보 없이 바뀔 수 있다.
- D015의 `MAX_END_TIME_EPOCH_MILLIS` ceiling은 향후 continuous/
  production-like 사용 전에 fresh하게 재검증해야 하며, rejection 이벤트에
  대한 observability가 아직 없다.
- runtime observability/JSON operational logging 없음.
- persistence/복구 없음.
- production numeric risk rule 없음.
- kill switch 없음.
- 전체 Python↔Java parity 없음(고정 4 MARKET 케이스만).
- OMS 운영 검증 미완: stale order 처리, restart retry/idempotency,
  WebSocket-vs-REST reconciliation, leverage/margin 검증, kill switch 거부.
- tooling guardrail(PreToolUse policy guard 등)은 defense-in-depth이지 OS
  sandbox가 아니다.
- CodeRabbit approval/리뷰 완료는 product readiness와 동일하지 않다.
- 경과한 paper 운영 증거 없음.
- branch protection/required-check 구현 상세는 고위험 작업 전에 재확인이
  필요하다 — 이 문서는 formal enforcement를 주장하지 않는다.

---

## 7. Current work state and recommended decision order

- 현재 active Candidate 없음. Candidate 21은 미선정/미시작이며 Issue/
  branch/PR도 없다.
- 구현 착수 전 PM 결정이 필요하다.

아래는 **비구속적(non-binding) 권고**이며 승인된 Candidate scope가 아니다.
"Candidate 21이 이것이 될 것이다", "다음 승인 작업", "예정됨", "확정
로드맵", "반드시 이 순서로 구현해야 한다" 같은 표현은 쓰지 않는다 — 아래는
검토용 후보 목록일 뿐이다.

1. audit/reproducibility checkpoint 및 structured JSON logging
2. market-data collector/storage/backfill 설계
3. position lifecycle/fill aggregation/영속·restart-safe paper runtime
4. alert 채널 결정 및 alert transport
5. production numeric risk rule과 kill switch(R4 human approval 필요)
6. paper qualification 운영
7. 그 이후에만 canary 준비 검토

---

*이 문서는 supporting reference다. 업데이트 시점: 의미 있는 PR merge 후,
major decision이 `docs/11_DECISION_LOG.md`에 기록된 후, 또는 blocker/phase가
바뀐 후. 금지: source-of-truth 결정의 독자적 변경, secret/실제 계정 값
기록, raw private operation data 기록.*
