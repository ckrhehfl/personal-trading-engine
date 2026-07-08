package com.ptengine.contract.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ptengine.risk.RiskDecision;
import com.ptengine.risk.RiskOutcome;
import org.junit.jupiter.api.Test;

class RiskDecisionJsonCodecTest {

    private static final String VALID_PASS_JSON =
            """
            {
              "schemaVersion": "0.1.0",
              "decisionId": "decision-100",
              "intentId": "intent-100",
              "outcome": "PASS",
              "reasonCodes": [],
              "evaluatedAtEpochMs": 1783396900100
            }
            """;

    private static final String VALID_BLOCK_JSON =
            """
            {
              "schemaVersion": "0.1.0",
              "decisionId": "decision-101",
              "intentId": "intent-101",
              "outcome": "BLOCK",
              "reasonCodes": ["KILL_SWITCH_ACTIVE"],
              "evaluatedAtEpochMs": 1783396900101
            }
            """;

    @Test
    void validPassParses() {
        RiskDecision decision = ContractJsonCodec.parseRiskDecision(VALID_PASS_JSON);
        assertEquals(RiskOutcome.PASS, decision.outcome());
        assertEquals(java.util.List.of(), decision.reasonCodes());
    }

    @Test
    void killSwitchActiveReasonRoundTrips() {
        RiskDecision decision = ContractJsonCodec.parseRiskDecision(VALID_BLOCK_JSON);
        assertEquals(java.util.List.of("KILL_SWITCH_ACTIVE"), decision.reasonCodes());
        String reserialized = ContractJsonCodec.toJson(decision);
        RiskDecision roundTripped = ContractJsonCodec.parseRiskDecision(reserialized);
        assertEquals(decision, roundTripped);
    }

    @Test
    void openReasonStringOutsideRiskRejectReasonRoundTrips() {
        String withOpenReason = VALID_BLOCK_JSON.replace("KILL_SWITCH_ACTIVE", "SOME_OPEN_REASON");
        RiskDecision decision = ContractJsonCodec.parseRiskDecision(withOpenReason);
        assertEquals(java.util.List.of("SOME_OPEN_REASON"), decision.reasonCodes());
    }

    @Test
    void unknownFieldRejected() {
        String withUnknownField =
                VALID_PASS_JSON.strip().replaceFirst("\\}\\s*$", ",\n  \"extra\": \"nope\"\n}");
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.parseRiskDecision(withUnknownField));
    }

    @Test
    void missingRequiredFieldRejected() {
        String withoutOutcome = VALID_PASS_JSON.replace("\"outcome\": \"PASS\",\n  ", "");
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.parseRiskDecision(withoutOutcome));
    }

    @Test
    void trailingContentAfterObjectRejected() {
        String withTrailingGarbage = VALID_PASS_JSON.strip() + " {}";
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.parseRiskDecision(withTrailingGarbage));
    }

    @Test
    void lowercaseOutcomeRejectedCaseSensitively() {
        String lowercased = VALID_PASS_JSON.replace("\"PASS\"", "\"pass\"");
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.parseRiskDecision(lowercased));
    }

    @Test
    void reasonCodesNonStringElementRejected() {
        String withNumericReason = VALID_BLOCK_JSON.replace("\"KILL_SWITCH_ACTIVE\"", "123");
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.parseRiskDecision(withNumericReason));
    }

    @Test
    void duplicateReasonCodesRejectedAtJsonBoundary() {
        String withDuplicates =
                VALID_BLOCK_JSON.replace(
                        "[\"KILL_SWITCH_ACTIVE\"]", "[\"KILL_SWITCH_ACTIVE\", \"KILL_SWITCH_ACTIVE\"]");
        assertThrows(ContractJsonException.class, () -> ContractJsonCodec.parseRiskDecision(withDuplicates));
    }

    @Test
    void reasonCodesArrayPreservesOrder() {
        String withTwoReasons =
                VALID_BLOCK_JSON.replace(
                        "[\"KILL_SWITCH_ACTIVE\"]", "[\"REASON_B\", \"REASON_A\"]");
        RiskDecision decision = ContractJsonCodec.parseRiskDecision(withTwoReasons);
        assertEquals(java.util.List.of("REASON_B", "REASON_A"), decision.reasonCodes());
    }

    @Test
    void repeatedSerializationIsByteForByteDeterministic() {
        RiskDecision decision = ContractJsonCodec.parseRiskDecision(VALID_BLOCK_JSON);
        String first = ContractJsonCodec.toJson(decision);
        String second = ContractJsonCodec.toJson(decision);
        assertEquals(first, second);
    }
}
