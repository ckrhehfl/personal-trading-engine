from .engine import run_backtest
from .evaluation import InSampleOutOfSampleResult, InvalidEvaluationSegmentsError, run_in_sample_out_of_sample
from .model import (
    BacktestConfig,
    BacktestMetadata,
    BacktestResult,
    Candle,
    EquityPoint,
    Fill,
    FillAction,
    InvalidBacktestConfigError,
    InvalidCandleError,
    TargetPosition,
    validate_candle_sequence,
)
from .report import (
    BacktestReport,
    InSampleOutOfSampleBacktestReport,
    InvalidBacktestReportError,
    generate_backtest_report,
    generate_in_sample_out_of_sample_report,
)
from .strategy import SmaCrossoverStrategy, Strategy

__all__ = [
    "BacktestConfig",
    "BacktestMetadata",
    "BacktestReport",
    "BacktestResult",
    "Candle",
    "EquityPoint",
    "Fill",
    "FillAction",
    "InSampleOutOfSampleBacktestReport",
    "InSampleOutOfSampleResult",
    "InvalidBacktestConfigError",
    "InvalidBacktestReportError",
    "InvalidCandleError",
    "InvalidEvaluationSegmentsError",
    "SmaCrossoverStrategy",
    "Strategy",
    "TargetPosition",
    "generate_backtest_report",
    "generate_in_sample_out_of_sample_report",
    "run_backtest",
    "run_in_sample_out_of_sample",
    "validate_candle_sequence",
]
