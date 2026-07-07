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
}

EXPECTED_INVALID_KEYWORDS = {
    "order-intent/invalid/limit-missing-price.json": "required",
    "order-intent/invalid/market-with-limit-price.json": "not",
    "order-intent/invalid/unknown-field.json": "additionalProperties",
    "risk-decision/invalid/rejected-without-reason.json": "minItems",
    "risk-decision/invalid/approved-with-reason.json": "maxItems",
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
                self.assertIn(EXPECTED_INVALID_KEYWORDS[relative], validators)


if __name__ == "__main__":
    unittest.main()
