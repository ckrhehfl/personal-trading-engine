"""Strategy boundary for the backtest engine.

A strategy is a pure function of closed-candle history. It never receives
future candles, exchange clients, network handles, or live-order
capabilities — the engine physically only ever passes it a prefix of the
candle sequence up to and including the current decision point.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, Sequence

from .model import Candle, TargetPosition


class Strategy(Protocol):
    def decide(self, history: Sequence[Candle]) -> TargetPosition | None:
        """Return a target position, or ``None`` if there is not enough
        closed-candle history yet to make a decision.

        ``history`` is the closed-candle history up to and including the
        current decision point (i.e. ``candles[: i + 1]`` for candle index
        ``i``). Implementations must not assume access to any candle beyond
        the last element of ``history``.
        """
        ...


@dataclass(frozen=True, slots=True)
class SmaCrossoverStrategy:
    """Simple moving-average crossover, for system-mechanics validation only.

    Not a profitability claim. Deterministic and stateless: ``decide`` is a
    pure function of the supplied history, so a single instance is safe to
    reuse across independent backtest runs (e.g. in-sample and
    out-of-sample) without leaking state between them.
    """

    short_window: int
    long_window: int

    def __post_init__(self) -> None:
        if self.short_window < 1:
            raise ValueError(f"short_window must be >= 1, was {self.short_window}")
        if self.long_window <= self.short_window:
            raise ValueError(
                f"long_window ({self.long_window}) must be > short_window ({self.short_window})"
            )

    def decide(self, history: Sequence[Candle]) -> TargetPosition | None:
        if len(history) < self.long_window:
            return None
        closes = [c.close for c in history]
        short_avg = sum(closes[-self.short_window :]) / self.short_window
        long_avg = sum(closes[-self.long_window :]) / self.long_window
        if short_avg > long_avg:
            return TargetPosition.LONG
        if short_avg < long_avg:
            return TargetPosition.SHORT
        return TargetPosition.FLAT
