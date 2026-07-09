package com.ptengine.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PaperExecutionMetadataTest {

    @Test
    void validMetadataAccepted() {
        PaperExecutionMetadata metadata = new PaperExecutionMetadata("exec-1", 4_000L);
        assertEquals("exec-1", metadata.executionId());
        assertEquals(4_000L, metadata.evaluatedAtEpochMs());
    }

    @Test
    void blankExecutionIdRejected() {
        assertThrows(InvalidPaperExecutionException.class, () -> new PaperExecutionMetadata(" ", 4_000L));
    }

    @Test
    void overlengthExecutionIdRejected() {
        String tooLong = "E".repeat(129);
        assertThrows(InvalidPaperExecutionException.class, () -> new PaperExecutionMetadata(tooLong, 4_000L));
    }

    @Test
    void negativeEvaluatedAtEpochMsRejected() {
        assertThrows(InvalidPaperExecutionException.class, () -> new PaperExecutionMetadata("exec-1", -1L));
    }

    @Test
    void nullExecutionIdRejected() {
        assertThrows(InvalidPaperExecutionException.class, () -> new PaperExecutionMetadata(null, 4_000L));
    }
}
