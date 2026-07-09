from __future__ import annotations

import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource


REPO_ROOT = Path(__file__).resolve().parents[2]
SCHEMA_DIR = REPO_ROOT / "schemas" / "v0.1"
FIXTURE_DIR = REPO_ROOT / "tests" / "schemas" / "fixtures"

SCHEMA_FILES = {
    "order-intent": "order-intent.schema.json",
    "risk-decision": "risk-decision.schema.json",
    "deployment-manifest": "deployment-manifest.schema.json",
}

EXPECTED_INVALID_KEYWORDS = {
    "order-intent/invalid/limit-missing-price.json": "required",
    "order-intent/invalid/market-with-limit-price.json": "not",
    "order-intent/invalid/unknown-field.json": "additionalProperties",
    "order-intent/invalid/wrong-schema-version.json": "const",
    "order-intent/invalid/zero-notional.json": "pattern",
    "order-intent/invalid/whitespace-intent-id.json": "pattern",
    "order-intent/invalid/identifier-too-long.json": "maxLength",
    "order-intent/invalid/unknown-direction.json": "enum",
    "order-intent/invalid/notional-as-number.json": "type",
    "order-intent/invalid/exponent-notional.json": "pattern",
    "order-intent/invalid/epoch-millis-overflow.json": "maximum",
    "risk-decision/invalid/block-without-reason.json": "minItems",
    "risk-decision/invalid/duplicate-reasons.json": "uniqueItems",
    "risk-decision/invalid/pass-with-reason.json": "maxItems",
    "risk-decision/invalid/unknown-field.json": "additionalProperties",
    "deployment-manifest/invalid/wrong-schema-version.json": "const",
    "deployment-manifest/invalid/unknown-field.json": "additionalProperties",
    "deployment-manifest/invalid/missing-backtest-run-id.json": "required",
    "deployment-manifest/invalid/blank-deployment-id.json": "pattern",
    "deployment-manifest/invalid/identifier-too-long.json": "maxLength",
    "deployment-manifest/invalid/wrong-status.json": "const",
    "deployment-manifest/invalid/epoch-millis-overflow.json": "maximum",
    "deployment-manifest/invalid/negative-created-at.json": "minimum",
    "deployment-manifest/invalid/null-model-version.json": "type",
    "deployment-manifest/invalid/blank-previous-deployment-id.json": "pattern",
    "deployment-manifest/invalid/blank-instrument.json": "pattern",
}


def load_json(path: Path) -> object:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


class SharedSchemaContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        schema_paths = sorted(SCHEMA_DIR.glob("*.schema.json"))
        cls.schemas = {path.name: load_json(path) for path in schema_paths}

        registry = Registry()
        for schema in cls.schemas.values():
            registry = registry.with_resource(
                schema["$id"], Resource.from_contents(schema)
            )
        cls.registry = registry

    def validator_for(self, contract: str) -> Draft202012Validator:
        schema_name = SCHEMA_FILES[contract]
        return Draft202012Validator(
            self.schemas[schema_name],
            registry=self.registry,
        )

    def test_all_schemas_are_valid_draft_2020_12(self) -> None:
        self.assertEqual(
            set(self.schemas),
            {
                "common.schema.json",
                "order-intent.schema.json",
                "risk-decision.schema.json",
                "deployment-manifest.schema.json",
            },
        )
        for name, schema in self.schemas.items():
            with self.subTest(schema=name):
                self.assertEqual(
                    schema["$schema"],
                    "https://json-schema.org/draft/2020-12/schema",
                )
                Draft202012Validator.check_schema(schema)

    def test_valid_fixtures_are_accepted(self) -> None:
        valid_files = sorted(FIXTURE_DIR.glob("*/valid/*.json"))
        self.assertGreater(len(valid_files), 0)

        for fixture_path in valid_files:
            contract = fixture_path.parents[1].name
            instance = load_json(fixture_path)
            errors = list(self.validator_for(contract).iter_errors(instance))
            with self.subTest(fixture=str(fixture_path.relative_to(FIXTURE_DIR))):
                self.assertEqual(errors, [])

    def test_invalid_fixtures_fail_for_expected_keyword(self) -> None:
        invalid_files = sorted(FIXTURE_DIR.glob("*/invalid/*.json"))
        actual = {
            str(path.relative_to(FIXTURE_DIR)).replace("\\", "/")
            for path in invalid_files
        }
        self.assertEqual(actual, set(EXPECTED_INVALID_KEYWORDS))

        for fixture_path in invalid_files:
            relative = str(fixture_path.relative_to(FIXTURE_DIR)).replace("\\", "/")
            contract = fixture_path.parents[1].name
            instance = load_json(fixture_path)
            errors = list(self.validator_for(contract).iter_errors(instance))
            validators = {error.validator for error in errors}
            with self.subTest(fixture=relative):
                self.assertEqual(
                    validators,
                    {EXPECTED_INVALID_KEYWORDS[relative]},
                )

    def test_epoch_millis_upper_bound(self) -> None:
        fixture_path = FIXTURE_DIR / "order-intent" / "valid" / "market.json"
        instance = load_json(fixture_path)
        instance["createdAtEpochMs"] = 2**63
        errors = list(self.validator_for("order-intent").iter_errors(instance))
        self.assertEqual({error.validator for error in errors}, {"maximum"})


if __name__ == "__main__":
    unittest.main()
