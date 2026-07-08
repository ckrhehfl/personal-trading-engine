import unittest

from ptengine.backtest.model import TargetPosition
from ptengine.backtest.strategy import SmaCrossoverStrategy

from .helpers import make_series


class SmaCrossoverWarmupTest(unittest.TestCase):
    def test_no_signal_before_long_window_reached(self):
        strategy = SmaCrossoverStrategy(short_window=1, long_window=3)
        candles = make_series([(100, 100), (100, 110), (100, 90)])

        self.assertIsNone(strategy.decide(candles[:1]))
        self.assertIsNone(strategy.decide(candles[:2]))

    def test_signal_emitted_once_long_window_reached(self):
        strategy = SmaCrossoverStrategy(short_window=1, long_window=3)
        candles = make_series([(100, 100), (100, 110), (100, 130)])

        decision = strategy.decide(candles[:3])
        self.assertIsNotNone(decision)
        self.assertIsInstance(decision, TargetPosition)

    def test_invalid_window_configuration_rejected(self):
        with self.assertRaises(ValueError):
            SmaCrossoverStrategy(short_window=0, long_window=3)
        with self.assertRaises(ValueError):
            SmaCrossoverStrategy(short_window=3, long_window=3)


class SmaCrossoverPureFunctionOfHistoryTest(unittest.TestCase):
    def test_decision_unaffected_by_candles_beyond_the_passed_slice(self):
        strategy = SmaCrossoverStrategy(short_window=1, long_window=2)

        # Two full series share an identical first two candles but diverge
        # afterwards. Slicing each down to the shared prefix must yield the
        # same decision, proving the strategy never looks past what it is
        # actually handed.
        series_with_future_up = make_series([(100, 100), (100, 130), (130, 500)])
        series_with_future_down = make_series([(100, 100), (100, 130), (130, 1)])

        decision_a = strategy.decide(series_with_future_up[:2])
        decision_b = strategy.decide(series_with_future_down[:2])
        self.assertEqual(decision_a, decision_b)

    def test_same_values_different_list_instances_give_same_decision(self):
        strategy = SmaCrossoverStrategy(short_window=1, long_window=2)
        history_1 = make_series([(100, 100), (100, 130)])
        history_2 = make_series([(100, 100), (100, 130)])  # distinct Candle/list objects, equal values

        self.assertIsNot(history_1, history_2)
        self.assertEqual(strategy.decide(history_1), strategy.decide(history_2))


if __name__ == "__main__":
    unittest.main()
