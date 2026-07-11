package com.ptengine.reconciliation;

import com.ptengine.contract.Direction;
import com.ptengine.paper.PaperExecutionResult;
import com.ptengine.paper.PaperExecutionSide;
import com.ptengine.paper.PaperExecutionStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic, pure-domain projection of one {@link PaperExecutionResult} to at most one open
 * {@link PositionSnapshot}.
 *
 * <p>No clock, no ID generation, no tolerance/staleness threshold, no network/file/environment
 * access. Does not aggregate multiple fills, update an existing position, or mutate OMS, {@code
 * PaperOrderPipeline}, {@code RiskGateway}, {@code PaperBroker}, or {@link PositionReconciler}
 * state.
 */
public final class PaperExecutionPositionProjector {

    public Optional<PositionSnapshot> project(PaperExecutionResult executionResult) {
        Objects.requireNonNull(executionResult, "executionResult");

        if (executionResult.status() == PaperExecutionStatus.NO_FILL) {
            return Optional.empty();
        }

        Direction direction = executionResult.side() == PaperExecutionSide.BUY ? Direction.LONG : Direction.SHORT;

        return Optional.of(new PositionSnapshot(
                executionResult.instrument(),
                direction,
                executionResult.requestedNotional(),
                executionResult.executionPrice(),
                executionResult.evaluatedAtEpochMs()));
    }
}
