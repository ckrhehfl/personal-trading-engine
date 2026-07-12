"""Candidate 13: deterministic Python<->Java MARKET-fill equivalence baseline.

Proves that the existing Python deterministic backtest engine
(``ptengine.backtest.engine.run_backtest``) and the existing Java
``PaperBroker.execute`` agree on economic side and exact execution price for
one fixed, repository-shared MARKET-fill fixture (see
``tests/execution/fixtures/python-java-market-fill-equivalence-v0.1.json``,
consumed independently by
``java/src/test/java/com/ptengine/paper/PythonJavaMarketFillEquivalenceTest.java``).

This is a narrower claim than full fill/no-fill parity. Python's current
non-fill causes (no next candle, below ``minimum_order_quantity``) and Java's
``NO_FILL`` (a LIMIT order that does not cross best bid/ask) are not the same
abstraction, so LIMIT and NO_FILL equivalence are explicitly out of scope
here.

Only the real public ``run_backtest`` entry point is exercised. The only
test-local addition is target *selection* via the existing
``ScriptedStrategy`` test helper (already used throughout
``test_engine.py``); no price, fill, order-type, or side logic is
reimplemented in a fake broker.
"""
from __future__ import annotations

import json
import unittest
from decimal import Decimal
from pathlib import Path

from ptengine.backtest.engine import run_backtest
from ptengine.backtest.model import BacktestResult, FillAction, TargetPosition

from .helpers import ScriptedStrategy, make_config, make_metadata, make_series

FIXTURE_VERSION = "python-java-market-fill-equivalence.v0.1"
FIXTURE_RELATIVE_PATH = Path("tests/execution/fixtures/python-java-market-fill-equivalence-v0.1.json")

REQUIRED_CASE_IDS = (
    "enter-long-market-buy",
    "enter-short-market-sell",
    "exit-long-market-sell",
    "exit-short-market-buy",
)

REQUIRED_FIELDS = frozenset(
    (
        "caseId",
        "intentType",
        "direction",
        "orderType",
        "referencePrice",
        "slippageBps",
        "bestBid",
        "bestAsk",
        "expectedStatus",
        "expectedSide",
        "expectedExecutionPrice",
    )
)

_BPS_DIVISOR = Decimal("10000")

# Closed intentType/direction -> economic side mapping. Mirrors
# ``PaperBroker.sideFor`` on the Java side and is verified independently
# there, not shared code.
_EXPECTED_SIDE_BY_INTENT_DIRECTION = {
    ("ENTER", "LONG"): "BUY",
    ("ENTER", "SHORT"): "SELL",
    ("EXIT", "LONG"): "SELL",
    ("EXIT", "SHORT"): "BUY",
}

_FILL_ACTION_BY_INTENT_DIRECTION = {
    ("ENTER", "LONG"): FillAction.OPEN_LONG,
    ("ENTER", "SHORT"): FillAction.OPEN_SHORT,
    ("EXIT", "LONG"): FillAction.CLOSE_LONG,
    ("EXIT", "SHORT"): FillAction.CLOSE_SHORT,
}

# Closed real-FillAction -> normalized economic side mapping, per the task's
# locked normalization: OPEN_LONG/CLOSE_SHORT -> BUY, OPEN_SHORT/CLOSE_LONG -> SELL.
_NORMALIZED_SIDE_BY_FILL_ACTION = {
    FillAction.OPEN_LONG: "BUY",
    FillAction.CLOSE_SHORT: "BUY",
    FillAction.OPEN_SHORT: "SELL",
    FillAction.CLOSE_LONG: "SELL",
}


def _repo_root() -> Path:
    """Resolve the repository root by walking up from this file, independent of CWD."""
    here = Path(__file__).resolve()
    for candidate in (here, *here.parents):
        if (candidate / "tests" / "execution" / "fixtures").is_dir():
            return candidate
    raise RuntimeError(f"could not locate repo root containing tests/execution/fixtures from {here}")


def load_fixture() -> dict:
    path = _repo_root() / FIXTURE_RELATIVE_PATH
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def _decimal_field(raw_case: dict, field: str) -> Decimal:
    value = raw_case[field]
    if not isinstance(value, str):
        raise AssertionError(f"{field} must be a JSON string, was {type(value).__name__}: {value!r}")
    return Decimal(value)


def validate_fixture(fixture: dict) -> list[dict]:
    """Fail closed on any structural or arithmetic inconsistency; return the case list.

    Independently re-derives the closed side mapping and the
    reference/slippage/expectedExecutionPrice and bid/ask alignment
    arithmetic from raw fixture bytes, rather than trusting the fixture's own
    ``expectedSide``/``expectedExecutionPrice`` fields at face value.
    """
    if fixture.get("fixtureVersion") != FIXTURE_VERSION:
        raise AssertionError(f"unexpected fixtureVersion: {fixture.get('fixtureVersion')!r}")

    cases = fixture.get("cases")
    if not isinstance(cases, list):
        raise AssertionError("fixture 'cases' must be a JSON array")

    seen_ids: list[str] = []
    for raw_case in cases:
        if not isinstance(raw_case, dict):
            raise AssertionError(f"each case must be a JSON object, was: {raw_case!r}")

        actual_fields = frozenset(raw_case)
        if actual_fields != REQUIRED_FIELDS:
            raise AssertionError(
                f"case has unexpected field set: extra={actual_fields - REQUIRED_FIELDS} "
                f"missing={REQUIRED_FIELDS - actual_fields}"
            )

        case_id = raw_case["caseId"]
        if case_id in seen_ids:
            raise AssertionError(f"duplicate caseId: {case_id}")
        seen_ids.append(case_id)

        if raw_case["orderType"] != "MARKET":
            raise AssertionError(f"unsupported orderType in case {case_id}: {raw_case['orderType']!r}")
        if raw_case["expectedStatus"] != "FILLED":
            raise AssertionError(f"unsupported expectedStatus in case {case_id}: {raw_case['expectedStatus']!r}")

        intent_type = raw_case["intentType"]
        direction = raw_case["direction"]
        if intent_type not in ("ENTER", "EXIT"):
            raise AssertionError(f"unsupported intentType in case {case_id}: {intent_type!r}")
        if direction not in ("LONG", "SHORT"):
            raise AssertionError(f"unsupported direction in case {case_id}: {direction!r}")

        expected_side_from_mapping = _EXPECTED_SIDE_BY_INTENT_DIRECTION[(intent_type, direction)]
        if raw_case["expectedSide"] != expected_side_from_mapping:
            raise AssertionError(
                f"case {case_id}: expectedSide {raw_case['expectedSide']!r} does not match "
                f"closed intentType/direction mapping {expected_side_from_mapping!r}"
            )

        reference_price = _decimal_field(raw_case, "referencePrice")
        slippage_bps = _decimal_field(raw_case, "slippageBps")
        best_bid = _decimal_field(raw_case, "bestBid")
        best_ask = _decimal_field(raw_case, "bestAsk")
        expected_execution_price = _decimal_field(raw_case, "expectedExecutionPrice")

        is_buy = expected_side_from_mapping == "BUY"
        adjustment = reference_price * slippage_bps / _BPS_DIVISOR
        arithmetic_price = reference_price + adjustment if is_buy else reference_price - adjustment
        if arithmetic_price != expected_execution_price:
            raise AssertionError(
                f"case {case_id}: expectedExecutionPrice {expected_execution_price} does not equal "
                f"referencePrice/slippageBps arithmetic {arithmetic_price}"
            )

        aligned_price = best_ask if is_buy else best_bid
        if aligned_price != expected_execution_price:
            field_name = "bestAsk" if is_buy else "bestBid"
            raise AssertionError(
                f"case {case_id}: {field_name} {aligned_price} does not equal "
                f"expectedExecutionPrice {expected_execution_price}"
            )

    if set(seen_ids) != set(REQUIRED_CASE_IDS) or len(seen_ids) != len(REQUIRED_CASE_IDS):
        raise AssertionError(f"fixture case IDs {seen_ids} do not exactly match required {list(REQUIRED_CASE_IDS)}")

    return cases


def _direction_target(raw_case: dict) -> TargetPosition:
    return TargetPosition.LONG if raw_case["direction"] == "LONG" else TargetPosition.SHORT


def _run_enter_case(raw_case: dict) -> BacktestResult:
    """FLAT -> LONG/SHORT through the real engine; fill executes at candle 1's open."""
    reference_price = raw_case["referencePrice"]
    slippage_bps = raw_case["slippageBps"]
    candles = make_series([(reference_price, reference_price), (reference_price, reference_price)])
    scripted_strategy = ScriptedStrategy({1: _direction_target(raw_case)})
    config = make_config(slippage_bps=slippage_bps)
    return run_backtest(candles, scripted_strategy, config, make_metadata())


def _run_exit_case(raw_case: dict) -> BacktestResult:
    """FLAT -> LONG/SHORT -> FLAT through the real engine; the closing fill is under test."""
    reference_price = raw_case["referencePrice"]
    slippage_bps = raw_case["slippageBps"]
    candles = make_series(
        [
            (reference_price, reference_price),
            (reference_price, reference_price),
            (reference_price, reference_price),
        ]
    )
    scripted_strategy = ScriptedStrategy({1: _direction_target(raw_case), 2: TargetPosition.FLAT})
    config = make_config(slippage_bps=slippage_bps)
    return run_backtest(candles, scripted_strategy, config, make_metadata())


def _run_case(raw_case: dict) -> BacktestResult:
    if raw_case["intentType"] == "ENTER":
        return _run_enter_case(raw_case)
    return _run_exit_case(raw_case)


class FixtureContractTest(unittest.TestCase):
    def test_fixture_structure_and_arithmetic_are_internally_consistent(self):
        fixture = load_fixture()
        cases = validate_fixture(fixture)

        self.assertEqual(len(cases), len(REQUIRED_CASE_IDS))
        self.assertEqual({c["caseId"] for c in cases}, set(REQUIRED_CASE_IDS))
        for raw_case in cases:
            self.assertEqual(raw_case["orderType"], "MARKET")
            self.assertEqual(raw_case["expectedStatus"], "FILLED")


class MarketFillEquivalenceTest(unittest.TestCase):
    def test_python_engine_matches_fixture_for_every_case(self):
        fixture = load_fixture()
        cases = validate_fixture(fixture)
        self.assertEqual(len(cases), len(REQUIRED_CASE_IDS))

        for raw_case in cases:
            with self.subTest(caseId=raw_case["caseId"]):
                intent_type = raw_case["intentType"]
                direction = raw_case["direction"]
                expected_side = raw_case["expectedSide"]
                expected_execution_price = Decimal(raw_case["expectedExecutionPrice"])
                expected_reference_price = Decimal(raw_case["referencePrice"])
                expected_action = _FILL_ACTION_BY_INTENT_DIRECTION[(intent_type, direction)]

                result = _run_case(raw_case)
                self.assertEqual(result.rejected_signal_count, 0)

                if intent_type == "ENTER":
                    self.assertEqual(len(result.fills), 1, "ENTER case must produce exactly one fill")
                    fill = result.fills[0]
                else:
                    self.assertEqual(
                        len(result.fills), 2, "EXIT case must produce exactly two fills (open then close)"
                    )
                    fill = result.fills[1]

                self.assertEqual(fill.action, expected_action)
                self.assertEqual(fill.reference_price, expected_reference_price)
                self.assertEqual(fill.execution_price, expected_execution_price)

                normalized_side = _NORMALIZED_SIDE_BY_FILL_ACTION[fill.action]
                self.assertEqual(normalized_side, expected_side)

    def test_buy_price_is_above_reference_and_sell_price_is_below(self):
        fixture = load_fixture()
        cases = validate_fixture(fixture)

        for raw_case in cases:
            with self.subTest(caseId=raw_case["caseId"]):
                reference_price = Decimal(raw_case["referencePrice"])
                expected_execution_price = Decimal(raw_case["expectedExecutionPrice"])
                if raw_case["expectedSide"] == "BUY":
                    self.assertGreater(expected_execution_price, reference_price)
                else:
                    self.assertLess(expected_execution_price, reference_price)


class DeterminismTest(unittest.TestCase):
    def test_running_every_fixture_case_twice_yields_equal_observations(self):
        fixture = load_fixture()
        cases = validate_fixture(fixture)

        for raw_case in cases:
            with self.subTest(caseId=raw_case["caseId"]):
                result_a = _run_case(raw_case)
                result_b = _run_case(raw_case)
                self.assertEqual(result_a, result_b)


if __name__ == "__main__":
    unittest.main()
