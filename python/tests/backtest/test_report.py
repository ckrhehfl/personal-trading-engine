import unittest
from decimal import Decimal

from ptengine.backtest.evaluation import InSampleOutOfSampleResult
from ptengine.backtest.model import BacktestResult, EquityPoint, Fill, FillAction
from ptengine.backtest.report import (
    BacktestReport,
    InSampleOutOfSampleBacktestReport,
    InvalidBacktestReportError,
    generate_backtest_report,
    generate_in_sample_out_of_sample_report,
)

from .helpers import make_metadata


def _d(x):
    return Decimal(str(x))


def _make_fill(
    *,
    action=FillAction.OPEN_LONG,
    quantity="1",
    reference_price="100",
    execution_price="100",
    fee="0",
) -> Fill:
    return Fill(
        signal_time_ms=0,
        execution_time_ms=60_000,
        action=action,
        quantity=_d(quantity),
        reference_price=_d(reference_price),
        execution_price=_d(execution_price),
        fee=_d(fee),
    )


def _make_equity_point(time_ms=0, equity="10000") -> EquityPoint:
    return EquityPoint(time_ms=time_ms, equity=_d(equity))


def _make_result(
    *,
    metadata=None,
    initial_equity="10000",
    final_equity="10500",
    total_return="0.05",
    benchmark_return="0.02",
    max_drawdown="0.01",
    turnover="0.5",
    fills=None,
    equity_curve=None,
    rejected_signal_count=0,
) -> BacktestResult:
    return BacktestResult(
        metadata=metadata or make_metadata(),
        initial_equity=_d(initial_equity),
        final_equity=_d(final_equity),
        total_return=_d(total_return),
        benchmark_return=_d(benchmark_return),
        max_drawdown=_d(max_drawdown),
        turnover=_d(turnover),
        fills=tuple(fills if fills is not None else [_make_fill(), _make_fill()]),
        equity_curve=tuple(
            equity_curve
            if equity_curve is not None
            else [_make_equity_point(0), _make_equity_point(60_000, "10500")]
        ),
        rejected_signal_count=rejected_signal_count,
    )


def _valid_report_kwargs(**overrides) -> dict:
    kwargs = dict(
        backtest_run_id="run-1",
        data_version="data-v1",
        strategy_version="strat-v1",
        parameter_version="param-v1",
        initial_equity=_d("10000"),
        final_equity=_d("10500"),
        total_return=_d("0.05"),
        benchmark_return=_d("0.02"),
        excess_return=_d("0.03"),
        max_drawdown=_d("0.01"),
        turnover=_d("0.5"),
        fill_count=2,
        equity_point_count=3,
        rejected_signal_count=1,
    )
    kwargs.update(overrides)
    return kwargs


class SingleRunGenerationTest(unittest.TestCase):
    def test_fields_derived_exactly(self):
        metadata = make_metadata(
            backtest_run_id="run-42",
            data_version="data-v9",
            strategy_version="strat-v3",
            parameter_version="param-v7",
        )
        fills = [_make_fill(), _make_fill(), _make_fill()]
        equity_curve = [_make_equity_point(0), _make_equity_point(60_000), _make_equity_point(120_000)]
        result = _make_result(
            metadata=metadata,
            initial_equity="10000",
            final_equity="10750.25",
            total_return="0.075025",
            benchmark_return="0.02",
            max_drawdown="0.033",
            turnover="1.25",
            fills=fills,
            equity_curve=equity_curve,
            rejected_signal_count=4,
        )

        report = generate_backtest_report(result)

        self.assertEqual(report.backtest_run_id, "run-42")
        self.assertEqual(report.data_version, "data-v9")
        self.assertEqual(report.strategy_version, "strat-v3")
        self.assertEqual(report.parameter_version, "param-v7")
        self.assertEqual(report.initial_equity, _d("10000"))
        self.assertEqual(report.final_equity, _d("10750.25"))
        self.assertEqual(report.total_return, _d("0.075025"))
        self.assertEqual(report.benchmark_return, _d("0.02"))
        self.assertEqual(report.max_drawdown, _d("0.033"))
        self.assertEqual(report.turnover, _d("1.25"))
        self.assertEqual(report.fill_count, 3)
        self.assertEqual(report.equity_point_count, 3)
        self.assertEqual(report.rejected_signal_count, 4)

    def test_excess_return_uses_exact_decimal_subtraction(self):
        result = _make_result(
            total_return="0.333333333333333333",
            benchmark_return="0.111111111111111111",
        )

        report = generate_backtest_report(result)

        self.assertEqual(
            report.excess_return,
            _d("0.333333333333333333") - _d("0.111111111111111111"),
        )
        self.assertEqual(report.excess_return, _d("0.222222222222222222"))

    def test_source_result_unchanged_after_generation(self):
        result = _make_result()
        fills_before = result.fills
        equity_curve_before = result.equity_curve
        metadata_before = result.metadata

        generate_backtest_report(result)

        self.assertIs(result.fills, fills_before)
        self.assertIs(result.equity_curve, equity_curve_before)
        self.assertIs(result.metadata, metadata_before)
        self.assertEqual(result.fills, fills_before)
        self.assertEqual(result.equity_curve, equity_curve_before)


class PlainTextRenderingTest(unittest.TestCase):
    def test_exact_full_string_and_key_order(self):
        report = BacktestReport(**_valid_report_kwargs())

        expected = "\n".join(
            [
                "backtestRunId=run-1",
                "dataVersion=data-v1",
                "strategyVersion=strat-v1",
                "parameterVersion=param-v1",
                "initialEquity=10000",
                "finalEquity=10500",
                "totalReturn=0.05",
                "benchmarkReturn=0.02",
                "excessReturn=0.03",
                "maxDrawdown=0.01",
                "turnover=0.5",
                "fillCount=2",
                "equityPointCount=3",
                "rejectedSignalCount=1",
            ]
        )

        self.assertEqual(report.to_plain_text(), expected)

    def test_no_trailing_newline(self):
        report = BacktestReport(**_valid_report_kwargs())

        text = report.to_plain_text()

        self.assertFalse(text.endswith("\n"))

    def test_scientific_notation_decimal_renders_fixed_point_without_rounding(self):
        report = BacktestReport(
            **_valid_report_kwargs(
                initial_equity=_d("1E+4"),
                final_equity=_d("1.05E+4"),
                total_return=_d("5E-2"),
                benchmark_return=_d("2E-2"),
                excess_return=_d("5E-2") - _d("2E-2"),
                max_drawdown=_d("1E-2"),
                turnover=_d("5E-1"),
            )
        )

        text = report.to_plain_text()

        self.assertIn("initialEquity=10000", text)
        self.assertIn("finalEquity=10500", text)
        self.assertIn("totalReturn=0.05", text)
        self.assertIn("benchmarkReturn=0.02", text)
        self.assertIn("excessReturn=0.03", text)
        self.assertIn("maxDrawdown=0.01", text)
        self.assertIn("turnover=0.5", text)

        # Only the values, not the "...Equity"/"...Return" key names, must be
        # free of scientific notation.
        values = [line.split("=", 1)[1] for line in text.split("\n")]
        for value in values:
            self.assertNotIn("E", value)
            self.assertNotIn("e", value)

    def test_repeated_rendering_is_byte_for_byte_deterministic(self):
        report = generate_backtest_report(_make_result())

        first = report.to_plain_text()
        second = report.to_plain_text()

        self.assertEqual(first, second)
        self.assertEqual(first.encode("utf-8"), second.encode("utf-8"))

    def test_negative_and_zero_decimals_render_exact_full_string_with_sign(self):
        report = BacktestReport(
            **_valid_report_kwargs(
                initial_equity=_d("10000"),
                final_equity=_d("9200"),
                total_return=_d("-0.08"),
                benchmark_return=_d("0"),
                excess_return=_d("-0.08") - _d("0"),
                max_drawdown=_d("-0.15"),
                turnover=_d("0"),
            )
        )

        expected = "\n".join(
            [
                "backtestRunId=run-1",
                "dataVersion=data-v1",
                "strategyVersion=strat-v1",
                "parameterVersion=param-v1",
                "initialEquity=10000",
                "finalEquity=9200",
                "totalReturn=-0.08",
                "benchmarkReturn=0",
                "excessReturn=-0.08",
                "maxDrawdown=-0.15",
                "turnover=0",
                "fillCount=2",
                "equityPointCount=3",
                "rejectedSignalCount=1",
            ]
        )

        self.assertEqual(report.to_plain_text(), expected)


class InSampleOutOfSampleReportTest(unittest.TestCase):
    def test_exact_section_ordering_and_full_string(self):
        is_result = _make_result(
            metadata=make_metadata(backtest_run_id="is-run"),
            total_return="0.01",
            benchmark_return="0.0",
        )
        oos_result = _make_result(
            metadata=make_metadata(backtest_run_id="oos-run"),
            total_return="-0.02",
            benchmark_return="0.0",
        )
        combined = InSampleOutOfSampleResult(in_sample=is_result, out_of_sample=oos_result)

        report = generate_in_sample_out_of_sample_report(combined)

        expected = "\n".join(
            [
                "[inSample]",
                report.in_sample.to_plain_text(),
                "[outOfSample]",
                report.out_of_sample.to_plain_text(),
            ]
        )
        text = report.to_plain_text()

        self.assertEqual(text, expected)
        self.assertTrue(text.startswith("[inSample]\nbacktestRunId=is-run"))
        self.assertIn("\n[outOfSample]\nbacktestRunId=oos-run", text)
        self.assertFalse(text.endswith("\n"))

    def test_each_segment_preserves_own_metadata_and_values(self):
        is_result = _make_result(
            metadata=make_metadata(backtest_run_id="is-run", data_version="is-data"),
            total_return="0.1",
            benchmark_return="0.05",
        )
        oos_result = _make_result(
            metadata=make_metadata(backtest_run_id="oos-run", data_version="oos-data"),
            total_return="-0.1",
            benchmark_return="0.05",
        )
        combined = InSampleOutOfSampleResult(in_sample=is_result, out_of_sample=oos_result)

        report = generate_in_sample_out_of_sample_report(combined)

        self.assertEqual(report.in_sample.backtest_run_id, "is-run")
        self.assertEqual(report.in_sample.data_version, "is-data")
        self.assertEqual(report.in_sample.total_return, _d("0.1"))
        self.assertEqual(report.in_sample.excess_return, _d("0.05"))
        self.assertEqual(report.out_of_sample.backtest_run_id, "oos-run")
        self.assertEqual(report.out_of_sample.data_version, "oos-data")
        self.assertEqual(report.out_of_sample.total_return, _d("-0.1"))
        self.assertEqual(report.out_of_sample.excess_return, _d("-0.15"))

    def test_generator_does_not_compare_rank_or_select_segments(self):
        # out-of-sample deliberately "wins" on every metric to prove the
        # generator never picks a preferred segment or reorders based on
        # performance -- both segments must appear, unmodified, in their
        # original in-sample/out-of-sample slots regardless of which looks
        # better.
        is_result = _make_result(
            metadata=make_metadata(backtest_run_id="worse"),
            total_return="-0.5",
            benchmark_return="0.0",
        )
        oos_result = _make_result(
            metadata=make_metadata(backtest_run_id="better"),
            total_return="0.5",
            benchmark_return="0.0",
        )
        combined = InSampleOutOfSampleResult(in_sample=is_result, out_of_sample=oos_result)

        report = generate_in_sample_out_of_sample_report(combined)

        self.assertIsInstance(report, InSampleOutOfSampleBacktestReport)
        self.assertEqual(report.in_sample.backtest_run_id, "worse")
        self.assertEqual(report.out_of_sample.backtest_run_id, "better")


class ValidationTest(unittest.TestCase):
    def test_blank_identifier_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(backtest_run_id="   "))

    def test_non_decimal_numeric_value_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(total_return=0.05))

    def test_float_forbidden_even_when_numerically_equal(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(turnover=0.5))

    def test_nan_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(max_drawdown=Decimal("NaN")))

    def test_positive_infinity_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(turnover=Decimal("Infinity")))

    def test_negative_infinity_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(
                **_valid_report_kwargs(
                    total_return=Decimal("-Infinity"),
                    excess_return=Decimal("-Infinity") - _d("0.02"),
                )
            )

    def test_negative_count_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(fill_count=-1))

    def test_negative_equity_point_count_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(equity_point_count=-1))

    def test_negative_rejected_signal_count_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(rejected_signal_count=-1))

    def test_bool_count_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(rejected_signal_count=True))

    def test_bool_fill_count_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(fill_count=False))

    def test_bool_equity_point_count_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(equity_point_count=True))

    def test_inconsistent_excess_return_rejected(self):
        with self.assertRaises(InvalidBacktestReportError):
            BacktestReport(**_valid_report_kwargs(excess_return=_d("999")))

    def test_generator_propagates_error_from_malformed_source_result(self):
        # BacktestResult itself has no __post_init__ validation (Candidate 4),
        # so a float field can reach the generator undetected until
        # generate_backtest_report constructs the BacktestReport -- this
        # proves that path fails closed too, not just direct construction.
        # turnover is passed through verbatim (no arithmetic performed on it
        # in generate_backtest_report), so this exercises BacktestReport's
        # own validation rather than an incidental TypeError from Decimal
        # arithmetic on a float.
        malformed_result = _make_result()
        object.__setattr__(malformed_result, "turnover", 0.5)

        with self.assertRaises(InvalidBacktestReportError):
            generate_backtest_report(malformed_result)


class PublicApiExportTest(unittest.TestCase):
    def test_required_names_import_from_package(self):
        from ptengine.backtest import (
            BacktestReport as pkg_backtest_report,
            InSampleOutOfSampleBacktestReport as pkg_is_oos_report,
            InvalidBacktestReportError as pkg_invalid_error,
            generate_backtest_report as pkg_generate_backtest_report,
            generate_in_sample_out_of_sample_report as pkg_generate_is_oos_report,
        )

        self.assertIs(pkg_backtest_report, BacktestReport)
        self.assertIs(pkg_is_oos_report, InSampleOutOfSampleBacktestReport)
        self.assertIs(pkg_invalid_error, InvalidBacktestReportError)
        self.assertIs(pkg_generate_backtest_report, generate_backtest_report)
        self.assertIs(pkg_generate_is_oos_report, generate_in_sample_out_of_sample_report)


if __name__ == "__main__":
    unittest.main()
