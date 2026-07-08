"""Deterministic domain model for the Python backtest skeleton.

Pure, immutable value types. No network, no exchange client, no live-order
capability. All monetary/price/quantity arithmetic uses ``decimal.Decimal``;
``float`` is never used for accounting.
"""
from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum
from typing import Sequence


class InvalidCandleError(ValueError):
    """Raised when a single candle or a candle sequence violates an invariant."""


class InvalidBacktestConfigError(ValueError):
    """Raised when backtest metadata or config values violate an invariant."""


@dataclass(frozen=True, slots=True)
class Candle:
    """A single closed OHLC candle with explicit open/close timestamps.

    ``open_time_ms``/``close_time_ms`` are epoch milliseconds. The engine only
    ever consumes candles that have already closed; there is no notion of a
    partially-formed candle here.
    """

    open_time_ms: int
    close_time_ms: int
    open: Decimal
    high: Decimal
    low: Decimal
    close: Decimal

    def __post_init__(self) -> None:
        if self.open_time_ms >= self.close_time_ms:
            raise InvalidCandleError(
                f"open_time_ms ({self.open_time_ms}) must be < close_time_ms ({self.close_time_ms})"
            )
        for name, value in (
            ("open", self.open),
            ("high", self.high),
            ("low", self.low),
            ("close", self.close),
        ):
            if not isinstance(value, Decimal):
                raise InvalidCandleError(f"{name} must be a Decimal, was {type(value).__name__}")
            if value <= 0:
                raise InvalidCandleError(f"{name} must be positive, was {value}")
        if self.high < self.low:
            raise InvalidCandleError(f"high ({self.high}) must be >= low ({self.low})")
        if self.high < self.open or self.high < self.close:
            raise InvalidCandleError("high must be >= open and >= close")
        if self.low > self.open or self.low > self.close:
            raise InvalidCandleError("low must be <= open and <= close")


def validate_candle_sequence(candles: Sequence[Candle]) -> None:
    """Reject non-chronological, overlapping, or duplicate candle windows.

    Each candle's open must be at or after the previous candle's close, so
    windows are strictly ordered and never overlap or repeat.
    """
    if not candles:
        raise InvalidCandleError("candle sequence must not be empty")
    for previous, current in zip(candles, candles[1:]):
        if current.open_time_ms < previous.close_time_ms:
            raise InvalidCandleError(
                "candle windows must be chronological and non-overlapping: "
                f"candle open_time_ms {current.open_time_ms} precedes "
                f"prior candle close_time_ms {previous.close_time_ms}"
            )


class TargetPosition(Enum):
    """Closed set of position targets a strategy may request."""

    LONG = "LONG"
    SHORT = "SHORT"
    FLAT = "FLAT"


class FillAction(Enum):
    """What a given fill did to the position (open a new side or close it)."""

    OPEN_LONG = "OPEN_LONG"
    CLOSE_LONG = "CLOSE_LONG"
    OPEN_SHORT = "OPEN_SHORT"
    CLOSE_SHORT = "CLOSE_SHORT"


def _require_non_blank_str(value: object, field_name: str) -> None:
    if not isinstance(value, str) or not value.strip():
        raise InvalidBacktestConfigError(f"{field_name} must be a non-blank string")


@dataclass(frozen=True, slots=True)
class BacktestMetadata:
    """Caller-supplied deterministic identity for a single backtest run.

    Every field must be supplied explicitly by the caller. Nothing here is
    derived from wall-clock time, randomness, or hidden global state.
    """

    backtest_run_id: str
    data_version: str
    strategy_version: str
    parameter_version: str

    def __post_init__(self) -> None:
        _require_non_blank_str(self.backtest_run_id, "backtest_run_id")
        _require_non_blank_str(self.data_version, "data_version")
        _require_non_blank_str(self.strategy_version, "strategy_version")
        _require_non_blank_str(self.parameter_version, "parameter_version")


@dataclass(frozen=True, slots=True)
class BacktestConfig:
    """Synthetic backtest configuration.

    These are test/research fixture values, not production exchange policy —
    no BingX-specific minimum order size, leverage, or margin setting is
    represented here or anywhere in this module.
    """

    initial_equity: Decimal
    order_quantity: Decimal
    fee_rate: Decimal
    slippage_bps: Decimal
    minimum_order_quantity: Decimal

    def __post_init__(self) -> None:
        if self.initial_equity <= 0:
            raise InvalidBacktestConfigError(f"initial_equity must be positive, was {self.initial_equity}")
        if self.order_quantity <= 0:
            raise InvalidBacktestConfigError(f"order_quantity must be positive, was {self.order_quantity}")
        if self.fee_rate < 0:
            raise InvalidBacktestConfigError(f"fee_rate must be non-negative, was {self.fee_rate}")
        if self.slippage_bps < 0:
            raise InvalidBacktestConfigError(f"slippage_bps must be non-negative, was {self.slippage_bps}")
        if self.minimum_order_quantity < 0:
            raise InvalidBacktestConfigError(
                f"minimum_order_quantity must be non-negative, was {self.minimum_order_quantity}"
            )


@dataclass(frozen=True, slots=True)
class Fill:
    """A single executed fill, sufficient to reconstruct signal/execution timing."""

    signal_time_ms: int
    execution_time_ms: int
    action: FillAction
    quantity: Decimal
    reference_price: Decimal
    execution_price: Decimal
    fee: Decimal


@dataclass(frozen=True, slots=True)
class EquityPoint:
    """One mark-to-market equity observation, keyed by candle close time."""

    time_ms: int
    equity: Decimal


@dataclass(frozen=True, slots=True)
class BacktestResult:
    """Full deterministic output of a single backtest run."""

    metadata: BacktestMetadata
    initial_equity: Decimal
    final_equity: Decimal
    total_return: Decimal
    benchmark_return: Decimal
    max_drawdown: Decimal
    turnover: Decimal
    fills: tuple[Fill, ...]
    equity_curve: tuple[EquityPoint, ...]
    rejected_signal_count: int
