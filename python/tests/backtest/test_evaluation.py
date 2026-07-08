import unittest

from ptengine.backtest.evaluation import InvalidEvaluationSegmentsError, run_in_sample_out_of_sample

from .helpers import CountingStrategy, ScriptedStrategy, make_config, make_metadata, make_series

INTERVAL_MS = 60_000


class OverlapRejectionTest(unittest.TestCase):
    def test_overlapping_segments_rejected(self):
        in_sample = make_series([(100, 100)] * 5, start_ms=0)
        # starts inside the in-sample time range (open_time_ms = 2 * interval,
        # while in-sample runs from 0 to 5 * interval).
        out_of_sample = make_series([(100, 100)] * 3, start_ms=2 * INTERVAL_MS)

        with self.assertRaises(InvalidEvaluationSegmentsError):
            run_in_sample_out_of_sample(
                in_sample,
                out_of_sample,
                lambda: ScriptedStrategy({}),
                make_config(),
                make_metadata(backtest_run_id="is"),
                make_metadata(backtest_run_id="oos"),
            )


class OutOfSampleBeforeInSampleRejectionTest(unittest.TestCase):
    def test_out_of_sample_entirely_before_in_sample_rejected(self):
        in_sample = make_series([(100, 100)] * 3, start_ms=10 * INTERVAL_MS)
        out_of_sample = make_series([(100, 100)] * 3, start_ms=0)

        with self.assertRaises(InvalidEvaluationSegmentsError):
            run_in_sample_out_of_sample(
                in_sample,
                out_of_sample,
                lambda: ScriptedStrategy({}),
                make_config(),
                make_metadata(backtest_run_id="is"),
                make_metadata(backtest_run_id="oos"),
            )


class ContiguousBoundaryRejectionTest(unittest.TestCase):
    def test_out_of_sample_starting_exactly_at_in_sample_end_is_rejected(self):
        # out_of_sample's first open_time_ms is exactly equal to in_sample's
        # last close_time_ms — a touching, non-overlapping boundary. The PM
        # task packet requires OOS to start *strictly* after IS ends, so
        # this exact-equality case must be rejected, not accepted.
        in_sample = make_series([(100, 100), (100, 110), (110, 120)], start_ms=0)
        out_of_sample = make_series([(120, 120), (120, 90)], start_ms=3 * INTERVAL_MS)

        self.assertEqual(out_of_sample[0].open_time_ms, in_sample[-1].close_time_ms)

        with self.assertRaises(InvalidEvaluationSegmentsError):
            run_in_sample_out_of_sample(
                in_sample,
                out_of_sample,
                lambda: ScriptedStrategy({}),
                make_config(),
                make_metadata(backtest_run_id="is"),
                make_metadata(backtest_run_id="oos"),
            )


class SeparateResultsTest(unittest.TestCase):
    def test_strictly_later_segments_accepted(self):
        # A real time gap between in-sample's end and out-of-sample's start
        # (not merely touching) must be accepted.
        in_sample = make_series([(100, 100), (100, 110), (110, 120)], start_ms=0)
        out_of_sample = make_series([(120, 120), (120, 90)], start_ms=4 * INTERVAL_MS)

        self.assertGreater(out_of_sample[0].open_time_ms, in_sample[-1].close_time_ms)

        result = run_in_sample_out_of_sample(
            in_sample,
            out_of_sample,
            lambda: ScriptedStrategy({}),
            make_config(),
            make_metadata(backtest_run_id="is"),
            make_metadata(backtest_run_id="oos"),
        )

        self.assertEqual(result.in_sample.metadata.backtest_run_id, "is")
        self.assertEqual(result.out_of_sample.metadata.backtest_run_id, "oos")
        self.assertEqual(len(result.in_sample.equity_curve), len(in_sample))
        self.assertEqual(len(result.out_of_sample.equity_curve), len(out_of_sample))

    def test_strategy_factory_is_called_fresh_per_segment_no_state_leak(self):
        # CountingStrategy signals LONG on its *second* decide() call, then
        # never again. If run_in_sample_out_of_sample correctly calls
        # strategy_factory() once per segment, each segment's counter starts
        # at 0 and independently reaches "2" partway through, producing a
        # fill in *both* segments. If it instead shared one instance across
        # both runs (the state-leak bug this test guards against), the
        # counter would already be well past 2 by the time the OOS segment
        # starts (having been advanced by every IS decide() call) and could
        # never equal 2 again, so the OOS segment would end up with zero
        # fills — this test would then fail.
        in_sample = make_series([(100, 100), (100, 100), (100, 100)], start_ms=0)
        out_of_sample = make_series([(100, 100), (100, 100), (100, 100)], start_ms=4 * INTERVAL_MS)

        result = run_in_sample_out_of_sample(
            in_sample,
            out_of_sample,
            lambda: CountingStrategy(),
            make_config(),
            make_metadata(backtest_run_id="is"),
            make_metadata(backtest_run_id="oos"),
        )

        self.assertEqual(len(result.in_sample.fills), 1)
        self.assertEqual(len(result.out_of_sample.fills), 1)


class EmptySegmentRejectionTest(unittest.TestCase):
    def test_empty_in_sample_segment_rejected(self):
        with self.assertRaises(InvalidEvaluationSegmentsError):
            run_in_sample_out_of_sample(
                [],
                make_series([(100, 100)], start_ms=0),
                lambda: ScriptedStrategy({}),
                make_config(),
                make_metadata(backtest_run_id="is"),
                make_metadata(backtest_run_id="oos"),
            )

    def test_empty_out_of_sample_segment_rejected(self):
        with self.assertRaises(InvalidEvaluationSegmentsError):
            run_in_sample_out_of_sample(
                make_series([(100, 100)], start_ms=0),
                [],
                lambda: ScriptedStrategy({}),
                make_config(),
                make_metadata(backtest_run_id="is"),
                make_metadata(backtest_run_id="oos"),
            )


if __name__ == "__main__":
    unittest.main()
