package com.ptengine.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskDecisionTest {

    private static RiskDecision decision(RiskOutcome outcome, List<String> reasonCodes) {
        return new RiskDecision(
                RiskDecision.SCHEMA_VERSION, "decision-1", "intent-1", outcome, reasonCodes, 1_000L);
    }

    @Test
    void passWithEmptyReasonsIsValid() {
        RiskDecision decision = decision(RiskOutcome.PASS, List.of());
        assertTrue(decision.reasonCodes().isEmpty());
    }

    @Test
    void passWithReasonsRejected() {
        assertThrows(
                InvalidRiskDecisionException.class,
                () -> decision(RiskOutcome.PASS, List.of("RISK_ENGINE_ERROR")));
    }

    @Test
    void blockWithReasonsIsValid() {
        RiskDecision decision = decision(RiskOutcome.BLOCK, List.of("RISK_ENGINE_ERROR"));
        assertEquals(List.of("RISK_ENGINE_ERROR"), decision.reasonCodes());
    }

    @Test
    void blockWithEmptyReasonsRejected() {
        assertThrows(InvalidRiskDecisionException.class, () -> decision(RiskOutcome.BLOCK, List.of()));
    }

    @Test
    void duplicateReasonsRejected() {
        assertThrows(
                InvalidRiskDecisionException.class,
                () -> decision(
                        RiskOutcome.BLOCK,
                        List.of("RISK_ENGINE_ERROR", "RISK_ENGINE_ERROR", "RISK_CONFIGURATION_MISSING")));
    }

    @Test
    void multipleDistinctReasonsPreserveGivenOrder() {
        RiskDecision decision =
                decision(RiskOutcome.BLOCK, List.of("RISK_CONFIGURATION_MISSING", "RISK_ENGINE_ERROR"));
        assertEquals(List.of("RISK_CONFIGURATION_MISSING", "RISK_ENGINE_ERROR"), decision.reasonCodes());
    }

    @Test
    void returnedReasonsAreImmutable() {
        RiskDecision decision = decision(RiskOutcome.BLOCK, List.of("RISK_ENGINE_ERROR"));
        assertThrows(UnsupportedOperationException.class, () -> decision.reasonCodes().add("EXTRA"));
    }

    @Test
    void mutatingCallerListAfterConstructionDoesNotAffectDecision() {
        List<String> mutable = new ArrayList<>(List.of("RISK_ENGINE_ERROR"));
        RiskDecision decision = decision(RiskOutcome.BLOCK, mutable);
        mutable.add("RISK_CONFIGURATION_MISSING");
        assertEquals(List.of("RISK_ENGINE_ERROR"), decision.reasonCodes());
    }

    @Test
    void blankDecisionIdRejected() {
        assertThrows(
                InvalidRiskDecisionException.class,
                () -> new RiskDecision(RiskDecision.SCHEMA_VERSION, " ", "intent-1", RiskOutcome.PASS, List.of(), 1_000L));
    }

    @Test
    void blankIntentIdRejected() {
        assertThrows(
                InvalidRiskDecisionException.class,
                () -> new RiskDecision(RiskDecision.SCHEMA_VERSION, "decision-1", "", RiskOutcome.PASS, List.of(), 1_000L));
    }

    @Test
    void negativeTimestampRejected() {
        assertThrows(
                InvalidRiskDecisionException.class,
                () -> new RiskDecision(
                        RiskDecision.SCHEMA_VERSION, "decision-1", "intent-1", RiskOutcome.PASS, List.of(), -1L));
    }

    @Test
    void wrongSchemaVersionRejected() {
        assertThrows(
                InvalidRiskDecisionException.class,
                () -> new RiskDecision("0.2.0", "decision-1", "intent-1", RiskOutcome.PASS, List.of(), 1_000L));
    }

    @Test
    void nullOutcomeRejected() {
        assertThrows(InvalidRiskDecisionException.class, () -> decision(null, List.of()));
    }

    @Test
    void nullReasonCodesRejected() {
        assertThrows(InvalidRiskDecisionException.class, () -> decision(RiskOutcome.BLOCK, null));
    }

    @Test
    void openSchemaValidReasonStringOutsideRiskRejectReasonAccepted() {
        RiskDecision decision = decision(RiskOutcome.BLOCK, List.of("SOME_OPEN_REASON_NOT_IN_THE_ENUM"));
        assertEquals(List.of("SOME_OPEN_REASON_NOT_IN_THE_ENUM"), decision.reasonCodes());
    }

    @Test
    void killSwitchActiveReasonAccepted() {
        RiskDecision decision = decision(RiskOutcome.BLOCK, List.of("KILL_SWITCH_ACTIVE"));
        assertEquals(List.of("KILL_SWITCH_ACTIVE"), decision.reasonCodes());
    }

    @Test
    void nullElementInReasonCodesRejected() {
        List<String> withNullElement = new ArrayList<>();
        withNullElement.add("RISK_ENGINE_ERROR");
        withNullElement.add(null);
        assertThrows(InvalidRiskDecisionException.class, () -> decision(RiskOutcome.BLOCK, withNullElement));
    }

    @Test
    void blankReasonCodeElementRejected() {
        assertThrows(InvalidRiskDecisionException.class, () -> decision(RiskOutcome.BLOCK, List.of("   ")));
    }

    @Test
    void reasonCodeAtMaxLengthAccepted() {
        String maxLength = "R".repeat(RiskDecision.MAX_IDENTIFIER_LENGTH);
        RiskDecision decision = decision(RiskOutcome.BLOCK, List.of(maxLength));
        assertEquals(List.of(maxLength), decision.reasonCodes());
    }

    @Test
    void reasonCodeExceedingMaxLengthRejected() {
        String tooLong = "R".repeat(RiskDecision.MAX_IDENTIFIER_LENGTH + 1);
        assertThrows(InvalidRiskDecisionException.class, () -> decision(RiskOutcome.BLOCK, List.of(tooLong)));
    }

    @Test
    void decisionIdAtMaxLengthAccepted() {
        String maxLength = "d".repeat(RiskDecision.MAX_IDENTIFIER_LENGTH);
        RiskDecision decision = new RiskDecision(
                RiskDecision.SCHEMA_VERSION, maxLength, "intent-1", RiskOutcome.PASS, List.of(), 1_000L);
        assertEquals(maxLength, decision.decisionId());
    }

    @Test
    void decisionIdExceedingMaxLengthRejected() {
        String tooLong = "d".repeat(RiskDecision.MAX_IDENTIFIER_LENGTH + 1);
        assertThrows(
                InvalidRiskDecisionException.class,
                () -> new RiskDecision(
                        RiskDecision.SCHEMA_VERSION, tooLong, "intent-1", RiskOutcome.PASS, List.of(), 1_000L));
    }

    @Test
    void intentIdExceedingMaxLengthRejected() {
        String tooLong = "i".repeat(RiskDecision.MAX_IDENTIFIER_LENGTH + 1);
        assertThrows(
                InvalidRiskDecisionException.class,
                () -> new RiskDecision(
                        RiskDecision.SCHEMA_VERSION, "decision-1", tooLong, RiskOutcome.PASS, List.of(), 1_000L));
    }
}
