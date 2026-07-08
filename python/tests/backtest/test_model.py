import unittest
from decimal import Decimal

from ptengine.backtest.model import (
    BacktestConfig,
    BacktestMetadata,
    Candle,
    InvalidBacktestConfigError,
    InvalidCandleError,
    validate_candle_sequence,
)


def _d(x):
    return Decimal(str(x))


class CandleTimestampOrderingTest(unittest.TestCase):
    def test_equal_open_and_close_time_rejected(self):
        with self.assertRaises(InvalidCandleError):
            Candle(open_time_ms=1000, close_time_ms=1000, open=_d(1), high=_d(1), low=_d(1), close=_d(1))

    def test_open_after_close_rejected(self):
        with self.assertRaises(InvalidCandleError):
            Candle(open_time_ms=2000, close_time_ms=1000, open=_d(1), high=_d(1), low=_d(1), close=_d(1))

    def test_valid_ordering_accepted(self):
        candle = Candle(open_time_ms=1000, close_time_ms=2000, open=_d(100), high=_d(110), low=_d(90), close=_d(105))
        self.assertEqual(candle.open_time_ms, 1000)
        self.assertEqual(candle.close_time_ms, 2000)


class CandleSequenceValidationTest(unittest.TestCase):
    def test_duplicate_window_rejected(self):
        c0 = Candle(open_time_ms=0, close_time_ms=1000, open=_d(1), high=_d(1), low=_d(1), close=_d(1))
        c1 = Candle(open_time_ms=0, close_time_ms=1000, open=_d(1), high=_d(1), low=_d(1), close=_d(1))
        with self.assertRaises(InvalidCandleError):
            validate_candle_sequence([c0, c1])

    def test_out_of_order_window_rejected(self):
        c0 = Candle(open_time_ms=1000, close_time_ms=2000, open=_d(1), high=_d(1), low=_d(1), close=_d(1))
        c1 = Candle(open_time_ms=500, close_time_ms=1500, open=_d(1), high=_d(1), low=_d(1), close=_d(1))
        with self.assertRaises(InvalidCandleError):
            validate_candle_sequence([c0, c1])

    def test_contiguous_chronological_sequence_accepted(self):
        c0 = Candle(open_time_ms=0, close_time_ms=1000, open=_d(1), high=_d(1), low=_d(1), close=_d(1))
        c1 = Candle(open_time_ms=1000, close_time_ms=2000, open=_d(1), high=_d(1), low=_d(1), close=_d(1))
        validate_candle_sequence([c0, c1])  # must not raise

    def test_empty_sequence_rejected(self):
        with self.assertRaises(InvalidCandleError):
            validate_candle_sequence([])


class MalformedOhlcTest(unittest.TestCase):
    def test_high_below_low_rejected(self):
        with self.assertRaises(InvalidCandleError):
            Candle(open_time_ms=0, close_time_ms=1000, open=_d(100), high=_d(90), low=_d(95), close=_d(92))

    def test_high_below_open_rejected(self):
        with self.assertRaises(InvalidCandleError):
            Candle(open_time_ms=0, close_time_ms=1000, open=_d(100), high=_d(99), low=_d(90), close=_d(95))

    def test_high_below_close_rejected(self):
        with self.assertRaises(InvalidCandleError):
            Candle(open_time_ms=0, close_time_ms=1000, open=_d(90), high=_d(95), low=_d(85), close=_d(100))

    def test_low_above_open_rejected(self):
        with self.assertRaises(InvalidCandleError):
            Candle(open_time_ms=0, close_time_ms=1000, open=_d(90), high=_d(110), low=_d(95), close=_d(100))

    def test_low_above_close_rejected(self):
        with self.assertRaises(InvalidCandleError):
            Candle(open_time_ms=0, close_time_ms=1000, open=_d(100), high=_d(110), low=_d(95), close=_d(90))

    def test_zero_price_rejected(self):
        with self.assertRaises(InvalidCandleError):
            Candle(open_time_ms=0, close_time_ms=1000, open=_d(0), high=_d(1), low=_d(0), close=_d(1))

    def test_negative_price_rejected(self):
        with self.assertRaises(InvalidCandleError):
            Candle(open_time_ms=0, close_time_ms=1000, open=_d(-1), high=_d(1), low=_d(-1), close=_d(1))

    def test_non_decimal_price_rejected(self):
        with self.assertRaises(InvalidCandleError):
            Candle(open_time_ms=0, close_time_ms=1000, open=100.0, high=_d(100), low=_d(100), close=_d(100))


class BacktestConfigValidationTest(unittest.TestCase):
    def test_non_positive_initial_equity_rejected(self):
        with self.assertRaises(InvalidBacktestConfigError):
            BacktestConfig(
                initial_equity=_d(0),
                order_quantity=_d(1),
                fee_rate=_d(0),
                slippage_bps=_d(0),
                minimum_order_quantity=_d(0),
            )

    def test_non_positive_order_quantity_rejected(self):
        with self.assertRaises(InvalidBacktestConfigError):
            BacktestConfig(
                initial_equity=_d(1000),
                order_quantity=_d(0),
                fee_rate=_d(0),
                slippage_bps=_d(0),
                minimum_order_quantity=_d(0),
            )

    def test_negative_fee_rate_rejected(self):
        with self.assertRaises(InvalidBacktestConfigError):
            BacktestConfig(
                initial_equity=_d(1000),
                order_quantity=_d(1),
                fee_rate=_d(-0.001),
                slippage_bps=_d(0),
                minimum_order_quantity=_d(0),
            )

    def test_negative_slippage_bps_rejected(self):
        with self.assertRaises(InvalidBacktestConfigError):
            BacktestConfig(
                initial_equity=_d(1000),
                order_quantity=_d(1),
                fee_rate=_d(0),
                slippage_bps=_d(-1),
                minimum_order_quantity=_d(0),
            )

    def test_negative_minimum_order_quantity_rejected(self):
        with self.assertRaises(InvalidBacktestConfigError):
            BacktestConfig(
                initial_equity=_d(1000),
                order_quantity=_d(1),
                fee_rate=_d(0),
                slippage_bps=_d(0),
                minimum_order_quantity=_d(-1),
            )


class BacktestMetadataValidationTest(unittest.TestCase):
    def test_blank_backtest_run_id_rejected(self):
        with self.assertRaises(InvalidBacktestConfigError):
            BacktestMetadata(
                backtest_run_id="  ",
                data_version="dv1",
                strategy_version="sv1",
                parameter_version="pv1",
            )

    def test_missing_field_rejected(self):
        with self.assertRaises(InvalidBacktestConfigError):
            BacktestMetadata(
                backtest_run_id="run-1",
                data_version="",
                strategy_version="sv1",
                parameter_version="pv1",
            )


if __name__ == "__main__":
    unittest.main()
