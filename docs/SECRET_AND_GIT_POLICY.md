# Secret and Git Policy

## Never commit

- `.env`
- real API keys
- exchange secrets
- SSH private keys
- VPS credentials
- raw account exports
- live deployment configs
- production logs with sensitive data

## Allowed in repo

- `.env.example` with empty values
- schema files
- mock configs
- paper trading examples
- docs
- tests

## API key policy

Initial API keys must be read-only or paper/test keys where possible.

Before live trading:

- withdrawal permission must be disabled
- IP whitelist should be enabled if supported
- key must be scoped to the minimum required permissions
- key must never be stored in GitHub Actions secrets until a separate deployment policy is written
