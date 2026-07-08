import unittest
from decimal import Decimal

from ptengine.backtest.engine import run_backtest
from ptengine.backtest.model import FillAction, TargetPosition

from .helpers import ScriptedStrategy, make_config, make_metadata, make_series


def _d(x):
    return Decimal(str(x))


class MaxDrawdownTest(unittest.TestCase):
    def test_exact_max_drawdown_on_known_equity_path(self):
        # Enter once and hold: mark-to-market equity path is fully
        # determined by candle closes, giving a hand-computable drawdown.
        # entry @ 1000 -> equity path: 1000, 1000, 1200 (peak), 900 (trough,
        # dd=(1200-900)/1200=0.25), 1000 (partial recovery, dd=1/6 < 0.25).
        candles = make_series([(1000, 1000), (1000, 1000), (1000, 1200), (1200, 900), (900, 1000)])
        s = ScriptedStrategy({1: TargetPosition.LONG})
        config = make_config(initial_equity="1000", order_quantity="1")
        result = run_backtest(candles, s, config, make_metadata())

        self.assertEqual(result.max_drawdown, _d("0.25"))


class ShortMarkToMarketTest(unittest.TestCase):
    def test_short_unrealized_pnl_mid_run_before_any_close(self):
        # Open SHORT and never close it within the run: this exercises
        # _unrealized_pnl's SHORT branch directly via the mark-to-market
        # equity curve, independent of the separate realized-PnL formula
        # used on an actual close fill.
        candles = make_series([(1000, 1000), (1000, 1000), (1000, 800)])
        s = ScriptedStrategy({1: TargetPosition.SHORT})
        config = make_config(initial_equity="1000", order_quantity="1")
        result = run_backtest(candles, s, config, make_metadata())

        self.assertEqual(len(result.fills), 1)  # only the opening fill, never closed
        self.assertEqual(result.fills[0].action, FillAction.OPEN_SHORT)
        # entry @ 1000 (slippage/fee 0), mark @ 800 -> unrealized = (1000-800)*1 = 200
        self.assertEqual(result.equity_curve[-1].equity, _d(1200))
        self.assertEqual(result.final_equity, _d(1200))


class TurnoverTest(unittest.TestCase):
    def test_exact_turnover_on_known_fill_path(self):
        candles = make_series([(100, 100), (110, 110), (120, 120)])
        s = ScriptedStrategy({1: TargetPosition.LONG, 2: TargetPosition.FLAT})
        config = make_config(initial_equity="10000", order_quantity="1")
        result = run_backtest(candles, s, config, make_metadata())

        # gross executed notional = |1 * 110| + |1 * 120| = 230
        self.assertEqual(result.turnover, _d(230) / _d(10000))


class BenchmarkReturnTest(unittest.TestCase):
    def test_exact_buy_and_hold_benchmark_return(self):
        candles = make_series([(100, 100), (105, 110), (108, 125)])
        s = ScriptedStrategy({})  # never signals; benchmark is independent of strategy activity
        result = run_backtest(candles, s, make_config(), make_metadata())

        # (last close - first close) / first close = (125 - 100) / 100
        self.assertEqual(result.benchmark_return, _d("0.25"))


if __name__ == "__main__":
    unittest.main()
