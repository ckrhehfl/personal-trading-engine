# Shared schemas

`schemas/v0.1/` is the source of truth for the MVP v0.1 Python ↔ Java shared
contract line.

## Dialect

All schemas use JSON Schema Draft 2020-12 and declare:

```json
"$schema": "https://json-schema.org/draft/2020-12/schema"
```

The v0.1 baseline contains only:

- `common.schema.json`
- `order-intent.schema.json`
- `risk-decision.schema.json`

Exchange-specific schemas, generated language models, `PositionSnapshot`, and
`DeploymentManifest` are intentionally out of scope.

## Versioning convention

- Directory `v0.1/` identifies the contract line.
- Every instance carries `schemaVersion: "0.1.0"`.
- **MAJOR**: breaking instance-contract changes, including removal/rename,
  type changes, semantic reinterpretation, or narrowing an existing enum.
- **MINOR**: backward-compatible additions such as optional fields or a new
  schema that does not invalidate existing instances.
- **PATCH**: annotations, examples, documentation, or validation-tool fixes
  that do not change the accepted instance set.
- A change that alters which existing instances are valid is never PATCH.
- Cross-version `$ref` links are not allowed.
- Protobuf and code generation are deferred by D011.

## Cross-language representation rules

- Contract objects are strict (`additionalProperties: false`).
- Exact decimal values are base-10 strings, not JSON numbers, to avoid
  Python/Java binary-float drift.
- Timestamps are UTC Unix epoch milliseconds.
- Schema IDs are `urn:pte:schema:...`; validation resolves them from an
  in-memory local registry and does not require network access.

## Validation

Install the pinned validator dependency:

```bash
python3 -m pip install -r requirements/schema-validation.txt
```

Run the deterministic schema contract suite:

```bash
python3 -m unittest tests/schemas/test_schema_contracts.py -v
```

The suite verifies that:

1. every schema is itself valid Draft 2020-12;
2. every fixture under `valid/` is accepted;
3. every fixture under `invalid/` is rejected for the expected validator
   keyword.
