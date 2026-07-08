"""Shared deterministic test fixtures for the backtest test suite.

Not a test module itself (no ``test_`` prefix), so ``unittest discover``
never collects it directly.
"""
from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Sequence

from ptengine.backtest.model import BacktestConfig, BacktestMetadata, Candle, TargetPosition

INTERVAL_MS = 60_000


def make_candle(index: int, open_price, close_price, *, interval_ms: int = INTERVAL_MS, start_ms: int = 0) -> Candle:
    """A candle at slot ``index`` with the given open/close prices.

    high/low are derived as max/min(open, close) so the OHLC invariants hold
    without every test having to specify wicks explicitly.
    """
    open_time_ms = start_ms + index * interval_ms
    close_time_ms = open_time_ms + interval_ms
    o = Decimal(str(open_price))
    c = Decimal(str(close_price))
    return Candle(
        open_time_ms=open_time_ms,
        close_time_ms=close_time_ms,
        open=o,
        high=max(o, c),
        low=min(o, c),
        close=c,
    )


def make_series(prices: Sequence[tuple], *, interval_ms: int = INTERVAL_MS, start_ms: int = 0) -> list[Candle]:
    """``prices`` is a sequence of (open, close) pairs -> contiguous chronological candles."""
    return [
        make_candle(i, o, c, interval_ms=interval_ms, start_ms=start_ms) for i, (o, c) in enumerate(prices)
    ]


def make_config(
    *,
    initial_equity="10000",
    order_quantity="1",
    fee_rate="0",
    slippage_bps="0",
    minimum_order_quantity="0",
) -> BacktestConfig:
    return BacktestConfig(
        initial_equity=Decimal(initial_equity),
        order_quantity=Decimal(order_quantity),
        fee_rate=Decimal(fee_rate),
        slippage_bps=Decimal(slippage_bps),
        minimum_order_quantity=Decimal(minimum_order_quantity),
    )


def make_metadata(
    *,
    backtest_run_id="run-1",
    data_version="data-v1",
    strategy_version="strategy-v1",
    parameter_version="param-v1",
) -> BacktestMetadata:
    return BacktestMetadata(
        backtest_run_id=backtest_run_id,
        data_version=data_version,
        strategy_version=strategy_version,
        parameter_version=parameter_version,
    )


@dataclass(frozen=True, slots=True)
class ScriptedStrategy:
    """Test-only strategy returning a fixed target keyed by ``len(history)``.

    Not part of the production strategy surface — exists solely so tests can
    pin down exact signal timing without depending on SMA crossover math.
    """

    script: dict

    def decide(self, history: Sequence[Candle]) -> TargetPosition | None:
        return self.script.get(len(history))


class CountingStrategy:
    """Test-only stateful strategy: signals LONG on exactly its 2nd ``decide``
    call, ``None`` on every other call.

    Deliberately mutable/stateful (unlike :class:`ScriptedStrategy`) so tests
    can prove a caller obtains a *fresh* instance per run — a shared/reused
    instance would carry its call counter across runs and never reach the
    "2nd call" trigger again after the first run has already advanced it.
    """

    def __init__(self) -> None:
        self.calls = 0

    def decide(self, history: Sequence[Candle]) -> TargetPosition | None:
        self.calls += 1
        if self.calls == 2:
            return TargetPosition.LONG
        return None
