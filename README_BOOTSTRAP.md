# personal-trading-engine Bootstrap Guide

이 문서는 `ckrhehfl/personal-trading-engine` private GitHub repo를 로컬 WSL 환경에서 시작하기 위한 부트스트랩 가이드입니다.

## 핵심 결정

- GitHub repo: `ckrhehfl/personal-trading-engine`
- 로컬 개발 위치: WSL 내부 경로 권장
- Windows 표시 경로: `C:\Dev\personal-trading-engine`
- WSL 권장 경로: `~/dev/personal-trading-engine` 또는 `/mnt/c/Dev/personal-trading-engine`
- 추천: 성능과 권한 문제를 줄이기 위해 `~/dev/personal-trading-engine` 우선
- 아키텍처: Python Research Plane + Java Trading Plane Hybrid-lite
- 실거래/live key: 초기 repo에 절대 커밋 금지

## 즉시 할 일

1. WSL에서 개발 디렉토리 생성
2. GitHub private repo clone
3. v3 문서 세트 업로드
4. `.gitignore`, `.env.example`, `CLAUDE.md`, `README.md` 추가
5. 첫 commit/push
6. Claude Code를 repo 루트에서 Plan mode로 실행

## WSL 기준 clone 명령어

```bash
mkdir -p ~/dev
cd ~/dev
git clone git@github.com:ckrhehfl/personal-trading-engine.git
cd personal-trading-engine
```

HTTPS를 사용할 경우:

```bash
mkdir -p ~/dev
cd ~/dev
git clone https://github.com/ckrhehfl/personal-trading-engine.git
cd personal-trading-engine
```

## 초기 폴더 구조

```text
personal-trading-engine/
  README.md
  CLAUDE.md
  .gitignore
  .env.example
  docs/
  schemas/
  python/
  java/
  configs/
  infra/
  scripts/
  runs/
```

## 절대 커밋하면 안 되는 것

- `.env`
- 실제 API key
- BingX secret key
- withdrawal 권한이 있는 key
- live trading 설정 파일
- 실거래 계정 잔고/거래내역 원본
- 개인 VPS 접속 정보
- SSH private key
- 운영 로그 중 민감정보 포함 파일

## 첫 커밋 예시

```bash
git add README.md CLAUDE.md .gitignore .env.example docs schemas python java configs infra scripts runs
git commit -m "chore: initialize trading engine repository"
git push origin main
```

## Claude Code 첫 프롬프트

```text
Plan mode로만 진행해줘. 아직 파일을 수정하지 마.

이 repo는 개인용 기관식 BTC/USDT 선물 자동매매 시스템이다.
Python은 research/backtest/ML, Java는 OMS/risk/execution/live runtime을 담당한다.

현재 README, CLAUDE.md, docs를 읽고 다음을 제안해줘:
1. 첫 10개 GitHub issue
2. 첫 5개 PR 순서
3. Java OMS state machine 설계 문서
4. Python/Java 공유 schema 목록
5. live trading 전에 반드시 막아야 할 위험 작업
```
