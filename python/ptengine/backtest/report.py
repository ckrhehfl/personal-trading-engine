"""Deterministic summary report baseline for the Python backtest skeleton.

Pure, immutable value types and generator functions that derive a report
from an already-produced :class:`~ptengine.backtest.model.BacktestResult` or
:class:`~ptengine.backtest.evaluation.InSampleOutOfSampleResult`. No new
metric is computed here: every field is either copied verbatim from the
existing engine output or derived by exact ``Decimal`` subtraction. There is
no clock, randomness, environment access, filesystem access, network access,
locale-dependent formatting, logging side effect, or mutation of the source
result. This module is not a research dashboard, a persistence layer, a
report delivery service, a pass/fail qualification gate, or a
``DeploymentManifest``.
"""
from __future__ import annotations

import decimal
from dataclasses import dataclass
from decimal import Decimal, localcontext

from .evaluation import InSampleOutOfSampleResult
from .model import BacktestResult


class InvalidBacktestReportError(ValueError):
    """Raised when a :class:`BacktestReport` is constructed with invalid values."""


def _require_identifier(value: object, field_name: str) -> None:
    if not isinstance(value, str) or not value.strip():
        raise InvalidBacktestReportError(f"{field_name} must be a non-blank string")


def _subtract_exact(left: Decimal, right: Decimal) -> Decimal:
    """Compute ``left - right`` exactly, independent of the caller's ambient Decimal context.

    Plain ``left - right`` is rounded to whatever precision the caller's
    active :class:`decimal.Context` happens to have (``decimal.getcontext()``
    / ``decimal.localcontext()``), so the same two operands can silently
    yield different results depending on unrelated code elsewhere in the
    process. This function instead derives a working precision from the
    operands themselves -- never a fixed constant -- performs the
    subtraction inside its own :func:`decimal.localcontext`, and traps every
    signal that would indicate an inexact result, so an insufficient
    precision fails closed instead of silently rounding. The caller's
    ambient context is left untouched.
    """
    if not isinstance(left, Decimal) or not isinstance(right, Decimal):
        raise InvalidBacktestReportError(
            "exact subtraction operands must both be Decimal "
            f"(left_type={type(left).__name__}, right_type={type(right).__name__})"
        )

    if not left.is_finite() or not right.is_finite():
        raise InvalidBacktestReportError(
            f"cannot exactly subtract non-finite Decimal operands (left={left}, right={right})"
        )

    left_tuple = left.as_tuple()
    right_tuple = right.as_tuple()
    exponent_gap = abs(left_tuple.exponent - right_tuple.exponent)
    # Aligning the two operands to a common exponent (as subtraction does
    # internally) can add up to `exponent_gap` digits to whichever operand
    # has the larger (less negative) exponent; +2 leaves headroom for a
    # possible borrow/sign digit at the top of the result.
    working_precision = max(len(left_tuple.digits), len(right_tuple.digits)) + exponent_gap + 2

    if working_precision > decimal.MAX_PREC:
        raise InvalidBacktestReportError(
            "exact Decimal subtraction requires more precision than decimal.Context "
            f"supports (required_precision={working_precision}, max_precision={decimal.MAX_PREC})"
        )

    with localcontext() as ctx:
        try:
            ctx.prec = working_precision
            ctx.Emin = decimal.MIN_EMIN
            ctx.Emax = decimal.MAX_EMAX
        except (ValueError, OverflowError) as exc:
            raise InvalidBacktestReportError(
                "could not configure an exact Decimal context for subtraction "
                f"(required_precision={working_precision})"
            ) from exc
        for signal in (
            decimal.InvalidOperation,
            decimal.Inexact,
            decimal.Rounded,
            decimal.Clamped,
            decimal.Overflow,
            decimal.Underflow,
        ):
            ctx.traps[signal] = True
        try:
            result = left - right
        except decimal.DecimalException as exc:
            raise InvalidBacktestReportError(
                f"could not compute an exact finite Decimal subtraction for "
                f"(left={left}, right={right})"
            ) from exc

    if not result.is_finite():
        raise InvalidBacktestReportError(
            f"exact Decimal subtraction produced a non-finite result for (left={left}, right={right})"
        )

    return result


def _require_finite_decimal(value: object, field_name: str) -> None:
    if not isinstance(value, Decimal):
        raise InvalidBacktestReportError(f"{field_name} must be a Decimal, was {type(value).__name__}")
    if not value.is_finite():
        raise InvalidBacktestReportError(f"{field_name} must be finite, was {value}")


def _require_non_negative_count(value: object, field_name: str) -> None:
    if isinstance(value, bool) or not isinstance(value, int):
        raise InvalidBacktestReportError(f"{field_name} must be an int, was {type(value).__name__}")
    if value < 0:
        raise InvalidBacktestReportError(f"{field_name} must be non-negative, was {value}")


@dataclass(frozen=True, slots=True)
class BacktestReport:
    """Deterministic, immutable summary of a single backtest run.

    Every Decimal field is rendered by :meth:`to_plain_text` with
    locale-independent fixed-point formatting (``format(value, "f")``); no
    rounding, quantization, or percentage conversion is applied anywhere in
    this type.
    """

    backtest_run_id: str
    data_version: str
    strategy_version: str
    parameter_version: str
    initial_equity: Decimal
    final_equity: Decimal
    total_return: Decimal
    benchmark_return: Decimal
    excess_return: Decimal
    max_drawdown: Decimal
    turnover: Decimal
    fill_count: int
    equity_point_count: int
    rejected_signal_count: int

    def __post_init__(self) -> None:
        _require_identifier(self.backtest_run_id, "backtest_run_id")
        _require_identifier(self.data_version, "data_version")
        _require_identifier(self.strategy_version, "strategy_version")
        _require_identifier(self.parameter_version, "parameter_version")

        _require_finite_decimal(self.initial_equity, "initial_equity")
        _require_finite_decimal(self.final_equity, "final_equity")
        _require_finite_decimal(self.total_return, "total_return")
        _require_finite_decimal(self.benchmark_return, "benchmark_return")
        _require_finite_decimal(self.excess_return, "excess_return")
        _require_finite_decimal(self.max_drawdown, "max_drawdown")
        _require_finite_decimal(self.turnover, "turnover")

        _require_non_negative_count(self.fill_count, "fill_count")
        _require_non_negative_count(self.equity_point_count, "equity_point_count")
        _require_non_negative_count(self.rejected_signal_count, "rejected_signal_count")

        expected_excess_return = _subtract_exact(self.total_return, self.benchmark_return)
        if self.excess_return != expected_excess_return:
            raise InvalidBacktestReportError(
                "excess_return must equal total_return - benchmark_return "
                f"(excess_return={self.excess_return}, total_return={self.total_return}, "
                f"benchmark_return={self.benchmark_return})"
            )

    def to_plain_text(self) -> str:
        """Render this report as ``\\n``-separated ``key=value`` lines, no trailing newline."""
        lines = (
            f"backtestRunId={self.backtest_run_id}",
            f"dataVersion={self.data_version}",
            f"strategyVersion={self.strategy_version}",
            f"parameterVersion={self.parameter_version}",
            f"initialEquity={format(self.initial_equity, 'f')}",
            f"finalEquity={format(self.final_equity, 'f')}",
            f"totalReturn={format(self.total_return, 'f')}",
            f"benchmarkReturn={format(self.benchmark_return, 'f')}",
            f"excessReturn={format(self.excess_return, 'f')}",
            f"maxDrawdown={format(self.max_drawdown, 'f')}",
            f"turnover={format(self.turnover, 'f')}",
            f"fillCount={self.fill_count}",
            f"equityPointCount={self.equity_point_count}",
            f"rejectedSignalCount={self.rejected_signal_count}",
        )
        return "\n".join(lines)


@dataclass(frozen=True, slots=True)
class InSampleOutOfSampleBacktestReport:
    """Independently generated in-sample/out-of-sample report pair.

    ``in_sample`` and ``out_of_sample`` are never compared, ranked, or
    selected between; both are always present in their original order.
    """

    in_sample: BacktestReport
    out_of_sample: BacktestReport

    def to_plain_text(self) -> str:
        """Render ``[inSample]``/``[outOfSample]`` sections, no trailing newline."""
        lines = (
            "[inSample]",
            self.in_sample.to_plain_text(),
            "[outOfSample]",
            self.out_of_sample.to_plain_text(),
        )
        return "\n".join(lines)


def generate_backtest_report(result: BacktestResult) -> BacktestReport:
    """Derive a :class:`BacktestReport` from an existing :class:`BacktestResult`.

    Every field is copied verbatim from ``result`` or its metadata, except
    ``excess_return`` (``total_return - benchmark_return``), ``fill_count``
    (``len(result.fills)``), and ``equity_point_count``
    (``len(result.equity_curve)``). No metric is recalculated from fills or
    the equity curve, no candle/strategy state is inspected, and ``result``
    is not mutated.
    """
    metadata = result.metadata
    return BacktestReport(
        backtest_run_id=metadata.backtest_run_id,
        data_version=metadata.data_version,
        strategy_version=metadata.strategy_version,
        parameter_version=metadata.parameter_version,
        initial_equity=result.initial_equity,
        final_equity=result.final_equity,
        total_return=result.total_return,
        benchmark_return=result.benchmark_return,
        excess_return=_subtract_exact(result.total_return, result.benchmark_return),
        max_drawdown=result.max_drawdown,
        turnover=result.turnover,
        fill_count=len(result.fills),
        equity_point_count=len(result.equity_curve),
        rejected_signal_count=result.rejected_signal_count,
    )


def generate_in_sample_out_of_sample_report(
    result: InSampleOutOfSampleResult,
) -> InSampleOutOfSampleBacktestReport:
    """Independently derive IS/OOS reports from ``result``, preserving segment order.

    ``result.in_sample`` and ``result.out_of_sample`` are generated
    independently by :func:`generate_backtest_report`; neither segment is
    compared, ranked, or selected against the other.
    """
    return InSampleOutOfSampleBacktestReport(
        in_sample=generate_backtest_report(result.in_sample),
        out_of_sample=generate_backtest_report(result.out_of_sample),
    )
