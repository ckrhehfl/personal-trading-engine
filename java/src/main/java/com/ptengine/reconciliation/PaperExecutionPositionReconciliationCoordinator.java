package com.ptengine.reconciliation;

import com.ptengine.paper.PaperExecutionResult;
import java.util.Objects;
import java.util.Optional;

/**
 * One-shot, deterministic, in-process composition of the existing {@link
 * PaperExecutionPositionProjector} and {@link PositionReconciler} for exactly one {@link
 * PaperExecutionResult}.
 *
 * <p>This class is not a continuous reconciliation service: no scheduler or runtime loop, no
 * exchange/account source of truth, no tolerance/staleness threshold, no persistence or restart
 * recovery, no fill aggregation, and no update/close/reduce/flat-position lifecycle. It carries no
 * alert, kill-switch, deployment, or live trading authority.
 */
public final class PaperExecutionPositionReconciliationCoordinator {

    private final PaperExecutionPositionProjector projector = new PaperExecutionPositionProjector();
    private final PositionReconciler reconciler = new PositionReconciler();

    /**
     * Projects {@code executionResult} with the real {@link PaperExecutionPositionProjector} and,
     * only when it yields an observed {@link PositionSnapshot} (i.e. a FILLED execution), reconciles
     * {@code expected} against that observed snapshot with the real {@link PositionReconciler}.
     *
     * <p>A NO_FILL execution yields no projected snapshot, so this method short-circuits before
     * evaluating {@code expected}, {@code reconciliationId}, or {@code reconciledAtEpochMs} and
     * returns {@link Optional#empty()} without fabricating a flat/no-position snapshot or a
     * reconciliation result.
     *
     * <p>When the execution does yield a projected snapshot (i.e. a FILLED execution), {@code
     * expected}, {@code reconciliationId}, and {@code reconciledAtEpochMs} are validated only
     * indirectly, by delegating to the existing {@link PositionReconciler#reconcile} /
     * {@link PositionReconciliationResult} contract; this method performs no independent validation
     * of its own.
     *
     * @throws NullPointerException if {@code executionResult} is null
     * @throws NullPointerException if {@code expected} is null and the execution yields a
     *     projected snapshot (i.e. a FILLED execution)
     * @throws PositionReconciliationResult.InvalidPositionReconciliationResultException if {@code
     *     reconciliationId} is null, blank, or exceeds the maximum identifier length, or if {@code
     *     reconciledAtEpochMs} is negative, when the execution yields a projected snapshot
     */
    public Optional<PositionReconciliationResult> reconcileIfFilled(
            PaperExecutionResult executionResult,
            PositionSnapshot expected,
            String reconciliationId,
            long reconciledAtEpochMs) {
        Objects.requireNonNull(executionResult, "executionResult");

        Optional<PositionSnapshot> observed = projector.project(executionResult);
        if (observed.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(reconciler.reconcile(expected, observed.get(), reconciliationId, reconciledAtEpochMs));
    }
}
