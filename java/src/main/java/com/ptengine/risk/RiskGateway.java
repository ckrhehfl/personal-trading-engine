package com.ptengine.risk;

import com.ptengine.contract.OrderIntent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure-domain, fail-closed risk gateway. No network, no exchange, no
 * persistence, no live order placement — evaluation only.
 *
 * <p>Fail-closed contract: zero configured rules, a rule that throws, or a
 * rule that returns {@code null} instead of {@link Optional} all resolve to
 * {@link RiskOutcome#BLOCK}. No code path in this class returns
 * {@link RiskOutcome#PASS} on any error or ambiguous condition.
 *
 * <p>Determinism: rules are evaluated in the fixed order supplied at
 * construction, using a caller-supplied {@link RiskDecisionMetadata} rather
 * than any internal clock/id generator. The same intent, rule set, rule
 * order, and metadata always produce the same outcome and the same
 * reason-code order.
 */
public final class RiskGateway {

    private final List<RiskRule> rules;

    public RiskGateway(List<RiskRule> rules) {
        Objects.requireNonNull(rules, "rules");
        this.rules = List.copyOf(rules);
    }

    public RiskDecision evaluate(OrderIntent intent, RiskDecisionMetadata metadata) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(metadata, "metadata");

        if (rules.isEmpty()) {
            return blockDecision(intent, metadata, List.of(RiskRejectReason.RISK_CONFIGURATION_MISSING.name()));
        }

        LinkedHashSet<RiskRejectReason> reasons = new LinkedHashSet<>();
        for (RiskRule rule : rules) {
            Optional<RiskRejectReason> result;
            try {
                result = rule.evaluate(intent);
            } catch (RuntimeException e) {
                return blockDecision(intent, metadata, List.of(RiskRejectReason.RISK_ENGINE_ERROR.name()));
            }
            if (result == null) {
                return blockDecision(intent, metadata, List.of(RiskRejectReason.RISK_ENGINE_ERROR.name()));
            }
            result.ifPresent(reasons::add);
        }

        if (reasons.isEmpty()) {
            return new RiskDecision(
                    RiskDecision.SCHEMA_VERSION,
                    metadata.decisionId(),
                    intent.intentId(),
                    RiskOutcome.PASS,
                    List.of(),
                    metadata.evaluatedAtEpochMs());
        }
        return blockDecision(intent, metadata, reasons.stream().map(Enum::name).toList());
    }

    private static RiskDecision blockDecision(
            OrderIntent intent, RiskDecisionMetadata metadata, List<String> reasonCodes) {
        return new RiskDecision(
                RiskDecision.SCHEMA_VERSION,
                metadata.decisionId(),
                intent.intentId(),
                RiskOutcome.BLOCK,
                reasonCodes,
                metadata.evaluatedAtEpochMs());
    }
}
