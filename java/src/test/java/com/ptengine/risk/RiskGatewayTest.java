package com.ptengine.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ptengine.contract.Direction;
import com.ptengine.contract.IntentType;
import com.ptengine.contract.OrderIntent;
import com.ptengine.contract.OrderType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RiskGatewayTest {

    private static final OrderIntent INTENT = new OrderIntent(
            OrderIntent.SCHEMA_VERSION,
            "intent-1",
            "strategy-1",
            "BTCUSDT",
            IntentType.ENTER,
            Direction.LONG,
            OrderType.MARKET,
            new BigDecimal("100"),
            null,
            1_000L);

    private static final RiskDecisionMetadata METADATA = new RiskDecisionMetadata("decision-1", 2_000L);

    private static RiskRule alwaysPass() {
        return intent -> Optional.empty();
    }

    private static RiskRule alwaysReject(RiskRejectReason reason) {
        return intent -> Optional.of(reason);
    }

    private static RiskRule alwaysThrow() {
        return intent -> {
            throw new RuntimeException("simulated rule failure");
        };
    }

    private static RiskRule alwaysReturnNull() {
        return intent -> null;
    }

    @Test
    void emptyRuleSetBlocksWithConfigurationMissing() {
        RiskGateway gateway = new RiskGateway(List.of());
        RiskDecision decision = gateway.evaluate(INTENT, METADATA);
        assertEquals(RiskOutcome.BLOCK, decision.outcome());
        assertEquals(List.of("RISK_CONFIGURATION_MISSING"), decision.reasonCodes());
    }

    @Test
    void allRulesPassingProducesPassWithNoReasons() {
        RiskGateway gateway = new RiskGateway(List.of(alwaysPass(), alwaysPass()));
        RiskDecision decision = gateway.evaluate(INTENT, METADATA);
        assertEquals(RiskOutcome.PASS, decision.outcome());
        assertTrue(decision.reasonCodes().isEmpty());
    }

    @Test
    void singleRejectingRuleBlocksWithItsReason() {
        RiskGateway gateway = new RiskGateway(List.of(alwaysReject(RiskRejectReason.RISK_STATE_DEGRADED)));
        RiskDecision decision = gateway.evaluate(INTENT, METADATA);
        assertEquals(RiskOutcome.BLOCK, decision.outcome());
        assertEquals(List.of("RISK_STATE_DEGRADED"), decision.reasonCodes());
    }

    @Test
    void multipleRejectingRulesAggregateInDeterministicRuleOrder() {
        RiskGateway gateway = new RiskGateway(List.of(
                alwaysReject(RiskRejectReason.RISK_STATE_DEGRADED),
                alwaysPass(),
                alwaysReject(RiskRejectReason.RISK_CONFIGURATION_MISSING)));
        RiskDecision decision = gateway.evaluate(INTENT, METADATA);
        assertEquals(RiskOutcome.BLOCK, decision.outcome());
        assertEquals(
                List.of("RISK_STATE_DEGRADED", "RISK_CONFIGURATION_MISSING"), decision.reasonCodes());
    }

    @Test
    void duplicateReasonFromMultipleRulesAppearsOnce() {
        RiskGateway gateway = new RiskGateway(List.of(
                alwaysReject(RiskRejectReason.RISK_STATE_DEGRADED),
                alwaysReject(RiskRejectReason.RISK_STATE_DEGRADED)));
        RiskDecision decision = gateway.evaluate(INTENT, METADATA);
        assertEquals(List.of("RISK_STATE_DEGRADED"), decision.reasonCodes());
    }

    @Test
    void ruleExceptionBlocksWithEngineError() {
        RiskGateway gateway = new RiskGateway(List.of(alwaysPass(), alwaysThrow()));
        RiskDecision decision = gateway.evaluate(INTENT, METADATA);
        assertEquals(RiskOutcome.BLOCK, decision.outcome());
        assertEquals(List.of("RISK_ENGINE_ERROR"), decision.reasonCodes());
    }

    @Test
    void ruleReturningNullBlocksWithEngineErrorInsteadOfNpe() {
        RiskGateway gateway = new RiskGateway(List.of(alwaysReturnNull()));
        RiskDecision decision = gateway.evaluate(INTENT, METADATA);
        assertEquals(RiskOutcome.BLOCK, decision.outcome());
        assertEquals(List.of("RISK_ENGINE_ERROR"), decision.reasonCodes());
    }

    @Test
    void noErrorPathEverReturnsPass() {
        RiskGateway emptyRules = new RiskGateway(List.of());
        RiskGateway throwingRule = new RiskGateway(List.of(alwaysThrow()));
        RiskGateway nullReturningRule = new RiskGateway(List.of(alwaysReturnNull()));

        assertEquals(RiskOutcome.BLOCK, emptyRules.evaluate(INTENT, METADATA).outcome());
        assertEquals(RiskOutcome.BLOCK, throwingRule.evaluate(INTENT, METADATA).outcome());
        assertEquals(RiskOutcome.BLOCK, nullReturningRule.evaluate(INTENT, METADATA).outcome());
    }

    @Test
    void decisionMetadataIsPreservedVerbatim() {
        RiskGateway gateway = new RiskGateway(List.of(alwaysPass()));
        RiskDecision decision = gateway.evaluate(INTENT, METADATA);
        assertEquals(METADATA.decisionId(), decision.decisionId());
        assertEquals(METADATA.evaluatedAtEpochMs(), decision.evaluatedAtEpochMs());
        assertEquals(INTENT.intentId(), decision.intentId());
        assertEquals(RiskDecision.SCHEMA_VERSION, decision.schemaVersion());
    }

    @Test
    void decisionMetadataIsPreservedVerbatimOnBlockPath() {
        RiskGateway gateway = new RiskGateway(List.of(alwaysReject(RiskRejectReason.RISK_STATE_DEGRADED)));
        RiskDecision decision = gateway.evaluate(INTENT, METADATA);
        assertEquals(RiskOutcome.BLOCK, decision.outcome());
        assertEquals(METADATA.decisionId(), decision.decisionId());
        assertEquals(METADATA.evaluatedAtEpochMs(), decision.evaluatedAtEpochMs());
        assertEquals(INTENT.intentId(), decision.intentId());
        assertEquals(RiskDecision.SCHEMA_VERSION, decision.schemaVersion());
    }

    @Test
    void repeatedEvaluationOfSameInputsIsDeterministic() {
        RiskGateway gateway = new RiskGateway(List.of(
                alwaysReject(RiskRejectReason.RISK_STATE_DEGRADED),
                alwaysReject(RiskRejectReason.RISK_CONFIGURATION_MISSING)));
        RiskDecision first = gateway.evaluate(INTENT, METADATA);
        RiskDecision second = gateway.evaluate(INTENT, METADATA);
        assertEquals(first, second);
    }

    @Test
    void ruleAfterAFailingRuleIsNotEvaluated() {
        List<Boolean> laterRuleWasCalled = new ArrayList<>();
        RiskRule laterRule = intent -> {
            laterRuleWasCalled.add(true);
            return Optional.of(RiskRejectReason.RISK_STATE_DEGRADED);
        };
        RiskGateway gateway = new RiskGateway(List.of(alwaysThrow(), laterRule));

        RiskDecision decision = gateway.evaluate(INTENT, METADATA);

        assertEquals(List.of("RISK_ENGINE_ERROR"), decision.reasonCodes());
        assertTrue(laterRuleWasCalled.isEmpty(), "rule after a throwing rule must not be evaluated");
    }

    @Test
    void ruleAfterANullReturningRuleIsNotEvaluated() {
        List<Boolean> laterRuleWasCalled = new ArrayList<>();
        RiskRule laterRule = intent -> {
            laterRuleWasCalled.add(true);
            return Optional.of(RiskRejectReason.RISK_STATE_DEGRADED);
        };
        RiskGateway gateway = new RiskGateway(List.of(alwaysReturnNull(), laterRule));

        RiskDecision decision = gateway.evaluate(INTENT, METADATA);

        assertEquals(List.of("RISK_ENGINE_ERROR"), decision.reasonCodes());
        assertTrue(laterRuleWasCalled.isEmpty(), "rule after a null-returning rule must not be evaluated");
    }

    @Test
    void nullRulesListRejected() {
        assertThrows(NullPointerException.class, () -> new RiskGateway(null));
    }

    @Test
    void nullIntentRejected() {
        RiskGateway gateway = new RiskGateway(List.of(alwaysPass()));
        assertThrows(NullPointerException.class, () -> gateway.evaluate(null, METADATA));
    }

    @Test
    void nullMetadataRejected() {
        RiskGateway gateway = new RiskGateway(List.of(alwaysPass()));
        assertThrows(NullPointerException.class, () -> gateway.evaluate(INTENT, null));
    }
}
