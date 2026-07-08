import ast
import inspect
import time
import unittest
from decimal import Decimal

from ptengine.backtest import engine, evaluation, model, strategy
from ptengine.backtest.engine import run_backtest
from ptengine.backtest.model import FillAction, TargetPosition

from .helpers import ScriptedStrategy, make_config, make_metadata, make_series


def _d(x):
    return Decimal(str(x))


class DeterminismTest(unittest.TestCase):
    def test_same_input_produces_exactly_equal_result(self):
        candles = make_series([(100, 110), (110, 90), (90, 95), (95, 130)])
        s = ScriptedStrategy({1: TargetPosition.LONG, 3: TargetPosition.FLAT})
        config = make_config(fee_rate="0.001", slippage_bps="10")
        metadata = make_metadata()

        result_a = run_backtest(candles, s, config, metadata)
        result_b = run_backtest(candles, s, config, metadata)

        self.assertEqual(result_a, result_b)

    def test_no_hidden_wall_clock_time_affects_result(self):
        candles = make_series([(100, 110), (110, 90), (90, 95)])
        s = ScriptedStrategy({1: TargetPosition.LONG})
        config = make_config(fee_rate="0.001", slippage_bps="10")
        metadata = make_metadata()

        result_a = run_backtest(candles, s, config, metadata)
        time.sleep(0.05)
        result_b = run_backtest(candles, s, config, metadata)

        self.assertEqual(result_a, result_b)

    def test_no_time_random_or_uuid_import_in_backtest_modules(self):
        forbidden = {"time", "random", "uuid", "datetime"}
        for module in (model, strategy, engine, evaluation):
            tree = ast.parse(inspect.getsource(module))
            imported = set()
            for node in ast.walk(tree):
                if isinstance(node, ast.Import):
                    imported.update(alias.name.split(".")[0] for alias in node.names)
                elif isinstance(node, ast.ImportFrom) and node.module:
                    imported.add(node.module.split(".")[0])
            self.assertFalse(
                imported & forbidden,
                f"{module.__name__} imports forbidden non-deterministic module(s): {imported & forbidden}",
            )


class NoFutureCandleAccessTest(unittest.TestCase):
    def test_strategy_never_receives_more_history_than_closed_so_far(self):
        candles = make_series([(100, 110), (110, 90), (90, 95), (95, 130)])
        seen_lengths = []

        class RecordingStrategy:
            def decide(self, history):
                seen_lengths.append(len(history))
                return None

        run_backtest(candles, RecordingStrategy(), make_config(), make_metadata())

        self.assertEqual(seen_lengths, [1, 2, 3, 4])


class NextBarOpenExecutionTest(unittest.TestCase):
    def test_signal_from_candle_i_executes_at_candle_i_plus_1_open_not_close(self):
        # candle0 close=110, candle1 open=200, candle1 close=300 — three
        # distinct values so the fill's reference price unambiguously
        # identifies which one was actually used.
        candles = make_series([(100, 110), (200, 300)])
        s = ScriptedStrategy({1: TargetPosition.LONG})
        result = run_backtest(candles, s, make_config(), make_metadata())

        self.assertEqual(len(result.fills), 1)
        fill = result.fills[0]
        self.assertEqual(fill.signal_time_ms, candles[0].close_time_ms)
        self.assertEqual(fill.execution_time_ms, candles[1].open_time_ms)
        self.assertEqual(fill.reference_price, _d(200))
        self.assertNotEqual(fill.reference_price, _d(110))
        self.assertNotEqual(fill.reference_price, _d(300))

    def test_final_signal_with_no_next_candle_is_not_executed(self):
        candles = make_series([(100, 110), (110, 120)])
        # len(history) == 2 only after the last candle closes; there is no
        # candle 2 for it to execute against.
        s = ScriptedStrategy({2: TargetPosition.LONG})
        result = run_backtest(candles, s, make_config(), make_metadata())

        self.assertEqual(result.fills, ())
        self.assertEqual(result.rejected_signal_count, 0)
        self.assertEqual(result.final_equity, result.initial_equity)


class FeeTest(unittest.TestCase):
    def test_fee_changes_equity_by_exact_expected_amount(self):
        candles = make_series([(100, 100), (100, 100)])
        s = ScriptedStrategy({1: TargetPosition.LONG})
        config = make_config(initial_equity="10000", order_quantity="1", fee_rate="0.001", slippage_bps="0")
        result = run_backtest(candles, s, config, make_metadata())

        self.assertEqual(len(result.fills), 1)
        fill = result.fills[0]
        self.assertEqual(fill.fee, _d("0.1"))  # 100 * 1 * 0.001
        self.assertEqual(result.final_equity, _d("9999.9"))

    def test_fee_is_based_on_post_slippage_execution_price_not_reference_price(self):
        # reference=100, 1% adverse buy slippage -> exec_price=101 (not 100).
        # fee must be computed on the 101 execution price, not the 100
        # reference price: 101 * 1 * 0.01 = 1.01, distinguishable from the
        # reference-price-based 100 * 1 * 0.01 = 1.00 a fee/reference mixup
        # would produce.
        candles = make_series([(100, 100), (100, 100)])
        s = ScriptedStrategy({1: TargetPosition.LONG})
        config = make_config(order_quantity="1", fee_rate="0.01", slippage_bps="100")
        result = run_backtest(candles, s, config, make_metadata())

        fill = result.fills[0]
        self.assertEqual(fill.reference_price, _d(100))
        self.assertEqual(fill.execution_price, _d(101))
        self.assertEqual(fill.fee, _d("1.01"))
        self.assertNotEqual(fill.fee, _d("1.00"))


class SlippageTest(unittest.TestCase):
    def test_buy_slippage_is_adverse(self):
        candles = make_series([(100, 100), (100, 100)])
        s = ScriptedStrategy({1: TargetPosition.LONG})  # opening long = buy
        config = make_config(slippage_bps="100")  # 1%
        result = run_backtest(candles, s, config, make_metadata())

        fill = result.fills[0]
        self.assertEqual(fill.action, FillAction.OPEN_LONG)
        self.assertGreater(fill.execution_price, fill.reference_price)
        self.assertEqual(fill.execution_price, _d(101))

    def test_sell_slippage_is_adverse(self):
        candles = make_series([(100, 100), (100, 100)])
        s = ScriptedStrategy({1: TargetPosition.SHORT})  # opening short = sell
        config = make_config(slippage_bps="100")
        result = run_backtest(candles, s, config, make_metadata())

        fill = result.fills[0]
        self.assertEqual(fill.action, FillAction.OPEN_SHORT)
        self.assertLess(fill.execution_price, fill.reference_price)
        self.assertEqual(fill.execution_price, _d(99))

    def test_closing_long_sell_slippage_is_adverse(self):
        candles = make_series([(100, 100), (100, 100), (100, 100)])
        s = ScriptedStrategy({1: TargetPosition.LONG, 2: TargetPosition.FLAT})
        config = make_config(slippage_bps="100")
        result = run_backtest(candles, s, config, make_metadata())

        close_fill = result.fills[1]
        self.assertEqual(close_fill.action, FillAction.CLOSE_LONG)
        self.assertLess(close_fill.execution_price, close_fill.reference_price)


class MinimumOrderQuantityTest(unittest.TestCase):
    def test_below_minimum_creates_no_fill_and_is_recorded(self):
        candles = make_series([(100, 100), (100, 100)])
        s = ScriptedStrategy({1: TargetPosition.LONG})
        config = make_config(order_quantity="1", minimum_order_quantity="2")
        result = run_backtest(candles, s, config, make_metadata())

        self.assertEqual(result.fills, ())
        self.assertEqual(result.rejected_signal_count, 1)
        self.assertEqual(result.final_equity, result.initial_equity)

    def test_exactly_at_minimum_is_accepted(self):
        candles = make_series([(100, 100), (100, 100)])
        s = ScriptedStrategy({1: TargetPosition.LONG})
        config = make_config(order_quantity="2", minimum_order_quantity="2")
        result = run_backtest(candles, s, config, make_metadata())

        self.assertEqual(len(result.fills), 1)
        self.assertEqual(result.rejected_signal_count, 0)

    def test_multiple_rejections_accumulate(self):
        # Three separate attempts to open, each below minimum: open rejected,
        # flat no-op, open rejected again, flat no-op, open rejected a third time.
        candles = make_series([(100, 100)] * 7)
        s = ScriptedStrategy(
            {
                1: TargetPosition.LONG,
                2: TargetPosition.FLAT,
                3: TargetPosition.LONG,
                4: TargetPosition.FLAT,
                5: TargetPosition.LONG,
            }
        )
        config = make_config(order_quantity="1", minimum_order_quantity="2")
        result = run_backtest(candles, s, config, make_metadata())

        self.assertEqual(result.fills, ())
        self.assertEqual(result.rejected_signal_count, 3)


class SingleCandleRunTest(unittest.TestCase):
    def test_single_candle_run_produces_no_fill_and_flat_equity_curve(self):
        candles = make_series([(100, 110)])
        s = ScriptedStrategy({1: TargetPosition.LONG})  # signal generated but no next candle exists
        result = run_backtest(candles, s, make_config(), make_metadata())

        self.assertEqual(result.fills, ())
        self.assertEqual(len(result.equity_curve), 1)
        self.assertEqual(result.equity_curve[0].equity, result.initial_equity)
        self.assertEqual(result.final_equity, result.initial_equity)


class LongRoundTripTest(unittest.TestCase):
    def test_long_entry_and_exit_exact_pnl(self):
        candles = make_series([(100, 100), (110, 110), (120, 120)])
        s = ScriptedStrategy({1: TargetPosition.LONG, 2: TargetPosition.FLAT})
        result = run_backtest(candles, s, make_config(), make_metadata())

        self.assertEqual(len(result.fills), 2)
        open_fill, close_fill = result.fills
        self.assertEqual(open_fill.action, FillAction.OPEN_LONG)
        self.assertEqual(open_fill.execution_price, _d(110))
        self.assertEqual(close_fill.action, FillAction.CLOSE_LONG)
        self.assertEqual(close_fill.execution_price, _d(120))
        self.assertEqual(result.final_equity, _d(10010))  # 10000 + (120-110)*1


class ShortRoundTripTest(unittest.TestCase):
    def test_short_entry_and_exit_exact_pnl(self):
        candles = make_series([(100, 100), (110, 110), (90, 90)])
        s = ScriptedStrategy({1: TargetPosition.SHORT, 2: TargetPosition.FLAT})
        result = run_backtest(candles, s, make_config(), make_metadata())

        self.assertEqual(len(result.fills), 2)
        open_fill, close_fill = result.fills
        self.assertEqual(open_fill.action, FillAction.OPEN_SHORT)
        self.assertEqual(open_fill.execution_price, _d(110))
        self.assertEqual(close_fill.action, FillAction.CLOSE_SHORT)
        self.assertEqual(close_fill.execution_price, _d(90))
        self.assertEqual(result.final_equity, _d(10020))  # 10000 + (110-90)*1


class FlipOrderingTest(unittest.TestCase):
    def test_long_to_short_flip_produces_separate_close_then_open_fills(self):
        candles = make_series([(100, 100), (110, 110), (120, 120)])
        s = ScriptedStrategy({1: TargetPosition.LONG, 2: TargetPosition.SHORT})
        config = make_config(fee_rate="0.001", slippage_bps="0")
        result = run_backtest(candles, s, config, make_metadata())

        self.assertEqual(len(result.fills), 3)  # open long, close long+open short
        close_fill, open_fill = result.fills[1], result.fills[2]
        self.assertEqual(close_fill.action, FillAction.CLOSE_LONG)
        self.assertEqual(open_fill.action, FillAction.OPEN_SHORT)
        self.assertEqual(close_fill.execution_time_ms, open_fill.execution_time_ms)
        self.assertEqual(close_fill.reference_price, open_fill.reference_price)
        self.assertGreater(close_fill.fee, 0)
        self.assertGreater(open_fill.fee, 0)

    def test_short_to_long_flip_produces_separate_close_then_open_fills(self):
        candles = make_series([(100, 100), (110, 110), (90, 90)])
        s = ScriptedStrategy({1: TargetPosition.SHORT, 2: TargetPosition.LONG})
        config = make_config(fee_rate="0.001", slippage_bps="0")
        result = run_backtest(candles, s, config, make_metadata())

        self.assertEqual(len(result.fills), 3)
        close_fill, open_fill = result.fills[1], result.fills[2]
        self.assertEqual(close_fill.action, FillAction.CLOSE_SHORT)
        self.assertEqual(open_fill.action, FillAction.OPEN_LONG)
        self.assertEqual(close_fill.execution_time_ms, open_fill.execution_time_ms)
        self.assertEqual(close_fill.reference_price, open_fill.reference_price)


class StableFillOrderingTest(unittest.TestCase):
    def test_fill_ordering_is_stable_across_runs(self):
        candles = make_series([(100, 100), (110, 110), (120, 120), (90, 90), (95, 95)])
        s = ScriptedStrategy(
            {1: TargetPosition.LONG, 2: TargetPosition.SHORT, 4: TargetPosition.FLAT}
        )
        config = make_config(fee_rate="0.001", slippage_bps="10")
        result_a = run_backtest(candles, s, config, make_metadata())
        result_b = run_backtest(candles, s, config, make_metadata())

        self.assertEqual(result_a.fills, result_b.fills)
        actions = [f.action for f in result_a.fills]
        self.assertEqual(
            actions,
            [
                FillAction.OPEN_LONG,
                FillAction.CLOSE_LONG,
                FillAction.OPEN_SHORT,
                FillAction.CLOSE_SHORT,
            ],
        )


class SnapshotTest(unittest.TestCase):
    def test_fixed_scenario_has_stable_expected_result(self):
        candles = make_series([(100, 100), (110, 110), (120, 120), (90, 90)])
        s = ScriptedStrategy({1: TargetPosition.LONG, 2: TargetPosition.FLAT})
        config = make_config(
            initial_equity="10000",
            order_quantity="1",
            fee_rate="0.001",
            slippage_bps="0",
        )
        metadata = make_metadata(backtest_run_id="snapshot-run")
        result = run_backtest(candles, s, config, metadata)

        self.assertEqual(result.metadata.backtest_run_id, "snapshot-run")
        self.assertEqual(result.initial_equity, _d(10000))
        # open @110 fee=0.11, close @120 fee=0.12, pnl=(120-110)*1=10
        self.assertEqual(result.final_equity, _d(10000) + _d(10) - _d("0.11") - _d("0.12"))
        self.assertEqual(len(result.fills), 2)
        self.assertEqual(result.fills[0].action, FillAction.OPEN_LONG)
        self.assertEqual(result.fills[1].action, FillAction.CLOSE_LONG)
        self.assertEqual(result.rejected_signal_count, 0)
        self.assertEqual(len(result.equity_curve), 4)
        self.assertEqual(result.equity_curve[-1].equity, result.final_equity)
        expected_turnover = (_d(110) + _d(120)) / _d(10000)
        self.assertEqual(result.turnover, expected_turnover)


if __name__ == "__main__":
    unittest.main()
