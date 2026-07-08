package com.ptengine.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RiskDecisionMetadataTest {

    @Test
    void validMetadataConstructs() {
        RiskDecisionMetadata metadata = new RiskDecisionMetadata("decision-1", 1_000L);
        assertEquals("decision-1", metadata.decisionId());
        assertEquals(1_000L, metadata.evaluatedAtEpochMs());
    }

    @Test
    void blankDecisionIdRejected() {
        assertThrows(InvalidRiskDecisionException.class, () -> new RiskDecisionMetadata("  ", 1_000L));
    }

    @Test
    void nullDecisionIdRejected() {
        assertThrows(InvalidRiskDecisionException.class, () -> new RiskDecisionMetadata(null, 1_000L));
    }

    @Test
    void negativeTimestampRejected() {
        assertThrows(InvalidRiskDecisionException.class, () -> new RiskDecisionMetadata("decision-1", -1L));
    }
}
