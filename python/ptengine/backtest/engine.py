"""Deterministic next-bar-open backtest engine.

Execution semantics (fixed, regression-tested):

For candle i:
  1. candle i becomes closed;
  2. the strategy sees history only through candle i (``candles[: i + 1]``);
  3. the strategy may emit a target position with signal timestamp equal to
     candle i's close timestamp;
  4. if a next candle (i + 1) exists, the pending target executes at candle
     i + 1's open;
  5. execution timestamp = candle i + 1's open timestamp.

A signal generated from candle i's close is therefore never filled using
candle i's own close price, and a final signal with no following candle is
never executed.

Accounting is deterministic linear PnL: initial equity plus realized PnL
(net of fees) plus unrealized mark-to-market PnL on any still-open position.
There is no leverage, margin, funding, or liquidation model — this is not a
production futures-account simulator.
"""
from __future__ import annotations

from decimal import Decimal
from typing import Sequence

from .model import (
    BacktestConfig,
    BacktestMetadata,
    BacktestResult,
    Candle,
    EquityPoint,
    Fill,
    FillAction,
    TargetPosition,
    validate_candle_sequence,
)
from .strategy import Strategy

_ZERO = Decimal("0")
_BPS_DIVISOR = Decimal("10000")


def _slippage_price(reference_price: Decimal, slippage_bps: Decimal, is_buy: bool) -> Decimal:
    """Adverse slippage: buy price moves up, sell price moves down."""
    adjustment = reference_price * slippage_bps / _BPS_DIVISOR
    return reference_price + adjustment if is_buy else reference_price - adjustment


def _unrealized_pnl(
    position: TargetPosition,
    entry_price: Decimal | None,
    mark_price: Decimal,
    quantity: Decimal,
) -> Decimal:
    if position == TargetPosition.FLAT or entry_price is None:
        return _ZERO
    if position == TargetPosition.LONG:
        return (mark_price - entry_price) * quantity
    return (entry_price - mark_price) * quantity


def _execute_target(
    *,
    target: TargetPosition,
    signal_time_ms: int,
    execution_time_ms: int,
    reference_price: Decimal,
    current_position: TargetPosition,
    entry_price: Decimal | None,
    realized_equity: Decimal,
    config: BacktestConfig,
) -> tuple[list[Fill], Decimal, TargetPosition, Decimal | None, bool]:
    """Execute a pending signal, closing the old side before opening the new one.

    A LONG<->SHORT flip produces two separate fills (close then open) at the
    same reference open price, each with its own slippage-adjusted execution
    price and fee. Returns (new_fills, realized_equity, position, entry_price,
    was_rejected_for_minimum_order_quantity).
    """
    if target == current_position:
        return [], realized_equity, current_position, entry_price, False

    fills: list[Fill] = []
    rejected = False

    if current_position != TargetPosition.FLAT:
        assert entry_price is not None
        is_buy = current_position == TargetPosition.SHORT
        exec_price = _slippage_price(reference_price, config.slippage_bps, is_buy)
        fee = exec_price * config.order_quantity * config.fee_rate
        if current_position == TargetPosition.LONG:
            pnl = (exec_price - entry_price) * config.order_quantity
            action = FillAction.CLOSE_LONG
        else:
            pnl = (entry_price - exec_price) * config.order_quantity
            action = FillAction.CLOSE_SHORT
        realized_equity = realized_equity + pnl - fee
        fills.append(
            Fill(
                signal_time_ms=signal_time_ms,
                execution_time_ms=execution_time_ms,
                action=action,
                quantity=config.order_quantity,
                reference_price=reference_price,
                execution_price=exec_price,
                fee=fee,
            )
        )
        current_position = TargetPosition.FLAT
        entry_price = None

    if target != TargetPosition.FLAT:
        if config.order_quantity < config.minimum_order_quantity:
            rejected = True
        else:
            is_buy = target == TargetPosition.LONG
            exec_price = _slippage_price(reference_price, config.slippage_bps, is_buy)
            fee = exec_price * config.order_quantity * config.fee_rate
            realized_equity = realized_equity - fee
            action = FillAction.OPEN_LONG if target == TargetPosition.LONG else FillAction.OPEN_SHORT
            fills.append(
                Fill(
                    signal_time_ms=signal_time_ms,
                    execution_time_ms=execution_time_ms,
                    action=action,
                    quantity=config.order_quantity,
                    reference_price=reference_price,
                    execution_price=exec_price,
                    fee=fee,
                )
            )
            current_position = target
            entry_price = exec_price

    return fills, realized_equity, current_position, entry_price, rejected


def _total_return(initial_equity: Decimal, final_equity: Decimal) -> Decimal:
    """(final - initial) / initial."""
    return (final_equity - initial_equity) / initial_equity


def _benchmark_return(candles: Sequence[Candle]) -> Decimal:
    """Deterministic buy-and-hold benchmark: (last close - first close) / first close.

    This does not model any transaction cost for the benchmark; it is not
    claimed to be cost-parity-comparable with the strategy's fee/slippage
    model.
    """
    first_close = candles[0].close
    last_close = candles[-1].close
    return (last_close - first_close) / first_close


def _max_drawdown(equity_curve: Sequence[EquityPoint]) -> Decimal:
    """Peak-to-trough drawdown over the equity curve, as a non-negative fraction of the running peak."""
    peak = equity_curve[0].equity
    max_dd = _ZERO
    for point in equity_curve:
        if point.equity > peak:
            peak = point.equity
        drawdown = (peak - point.equity) / peak
        if drawdown > max_dd:
            max_dd = drawdown
    return max_dd


def _turnover(fills: Sequence[Fill], initial_equity: Decimal) -> Decimal:
    """Gross executed notional (sum of absolute fill notionals) / initial equity."""
    gross_notional = sum((fill.quantity * fill.execution_price for fill in fills), _ZERO)
    return gross_notional / initial_equity


def run_backtest(
    candles: Sequence[Candle],
    strategy: Strategy,
    config: BacktestConfig,
    metadata: BacktestMetadata,
) -> BacktestResult:
    """Run a single deterministic backtest over ``candles``.

    Same exact (candles, strategy, config, metadata) always produces an
    exactly equal result: no wall-clock time, randomness, or hidden global
    state is read anywhere in this function or the modules it calls.
    """
    validate_candle_sequence(candles)

    pending_signal: tuple[TargetPosition, int] | None = None
    fills: list[Fill] = []
    equity_curve: list[EquityPoint] = []
    realized_equity = config.initial_equity
    current_position = TargetPosition.FLAT
    entry_price: Decimal | None = None
    rejected_signal_count = 0

    for i, candle in enumerate(candles):
        if pending_signal is not None:
            target, signal_time_ms = pending_signal
            pending_signal = None
            (
                new_fills,
                realized_equity,
                current_position,
                entry_price,
                rejected,
            ) = _execute_target(
                target=target,
                signal_time_ms=signal_time_ms,
                execution_time_ms=candle.open_time_ms,
                reference_price=candle.open,
                current_position=current_position,
                entry_price=entry_price,
                realized_equity=realized_equity,
                config=config,
            )
            fills.extend(new_fills)
            if rejected:
                rejected_signal_count += 1

        mark_equity = realized_equity + _unrealized_pnl(
            current_position, entry_price, candle.close, config.order_quantity
        )
        equity_curve.append(EquityPoint(time_ms=candle.close_time_ms, equity=mark_equity))

        target = strategy.decide(candles[: i + 1])
        if target is not None:
            pending_signal = (target, candle.close_time_ms)

    final_equity = equity_curve[-1].equity

    return BacktestResult(
        metadata=metadata,
        initial_equity=config.initial_equity,
        final_equity=final_equity,
        total_return=_total_return(config.initial_equity, final_equity),
        benchmark_return=_benchmark_return(candles),
        max_drawdown=_max_drawdown(equity_curve),
        turnover=_turnover(fills, config.initial_equity),
        fills=tuple(fills),
        equity_curve=tuple(equity_curve),
        rejected_signal_count=rejected_signal_count,
    )
