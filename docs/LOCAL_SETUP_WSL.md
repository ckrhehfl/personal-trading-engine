# Local Setup on Windows + WSL

## Recommended location

Use the WSL filesystem for better performance:

```bash
mkdir -p ~/dev
cd ~/dev
git clone git@github.com:ckrhehfl/personal-trading-engine.git
cd personal-trading-engine
```

Avoid running heavy Python/Java builds directly under `/mnt/c` unless you need Windows tool access.

## Alternative Windows-visible path

If you want the project visible at `C:\Dev\personal-trading-engine`, WSL path is:

```bash
/mnt/c/Dev/personal-trading-engine
```

This is convenient but may be slower for many small files.

## First local commands

```bash
git status
cp .env.example .env
```

Do not fill real API keys until paper trading infrastructure is ready.
