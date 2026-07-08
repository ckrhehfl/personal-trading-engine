"""Minimal explicit in-sample / out-of-sample separation baseline.

This is a separation baseline, not a research platform: it runs one fixed
strategy configuration and one fixed backtest config separately over two
non-overlapping, chronologically-ordered candle segments. It does not fit,
tune, optimize, or select among strategies, and it does not implement
walk-forward validation.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Sequence

from .engine import run_backtest
from .model import BacktestConfig, BacktestMetadata, BacktestResult, Candle, validate_candle_sequence
from .strategy import Strategy


class InvalidEvaluationSegmentsError(ValueError):
    """Raised when in-sample/out-of-sample candle segments violate separation rules."""


@dataclass(frozen=True, slots=True)
class InSampleOutOfSampleResult:
    in_sample: BacktestResult
    out_of_sample: BacktestResult


def run_in_sample_out_of_sample(
    in_sample_candles: Sequence[Candle],
    out_of_sample_candles: Sequence[Candle],
    strategy_factory: Callable[[], Strategy],
    config: BacktestConfig,
    in_sample_metadata: BacktestMetadata,
    out_of_sample_metadata: BacktestMetadata,
) -> InSampleOutOfSampleResult:
    """Run the same strategy configuration and backtest config on IS and OOS segments.

    ``strategy_factory`` is invoked once per segment so that a
    stateful strategy implementation cannot leak state between the two runs;
    a stateless strategy may return the same underlying configuration from
    both calls.

    Rejects: empty segments, non-chronological candles within a segment
    (via the same check the engine applies), any time overlap between the
    two segments, and an out-of-sample segment that does not start strictly
    after the in-sample segment ends.
    """
    if not in_sample_candles or not out_of_sample_candles:
        raise InvalidEvaluationSegmentsError(
            "both in-sample and out-of-sample segments must be non-empty"
        )

    validate_candle_sequence(in_sample_candles)
    validate_candle_sequence(out_of_sample_candles)

    in_sample_end_ms = in_sample_candles[-1].close_time_ms
    out_of_sample_start_ms = out_of_sample_candles[0].open_time_ms
    if out_of_sample_start_ms < in_sample_end_ms:
        raise InvalidEvaluationSegmentsError(
            "out-of-sample segment must start at or after the in-sample segment ends "
            f"(out-of-sample open_time_ms {out_of_sample_start_ms} < "
            f"in-sample close_time_ms {in_sample_end_ms})"
        )

    in_sample_result = run_backtest(in_sample_candles, strategy_factory(), config, in_sample_metadata)
    out_of_sample_result = run_backtest(
        out_of_sample_candles, strategy_factory(), config, out_of_sample_metadata
    )
    return InSampleOutOfSampleResult(in_sample=in_sample_result, out_of_sample=out_of_sample_result)
