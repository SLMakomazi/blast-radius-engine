package com.madlanga.blastradius.telemetry.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceProvenanceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:31:00Z");

    @Test
    void preservesAllProvenanceFields() {
        EvidenceProvenance p = EvidenceProvenance.of(
                EvidenceFamily.LOGS, "local-loki", NOW, "stream-ref");

        assertEquals(EvidenceFamily.LOGS, p.getFamily());
        assertEquals("local-loki", p.getProvider());
        assertEquals(NOW, p.getCollectedAt());
        assertEquals("stream-ref", p.getSourceRef());
    }

    @Test
    void nullSourceRefBecomesEmptyString() {
        EvidenceProvenance p = EvidenceProvenance.of(
                EvidenceFamily.METRICS, "local-prometheus", NOW, null);
        assertEquals("", p.getSourceRef());
    }

    @Test
    void rejectsNullFamily() {
        assertThrows(NullPointerException.class, () ->
                EvidenceProvenance.of(null, "provider", NOW, "ref"));
    }

    @Test
    void rejectsNullProvider() {
        assertThrows(NullPointerException.class, () ->
                EvidenceProvenance.of(EvidenceFamily.TRACES, null, NOW, "ref"));
    }

    @Test
    void equalityBasedOnAllFields() {
        EvidenceProvenance a = EvidenceProvenance.of(EvidenceFamily.HEALTH, "p", NOW, "r");
        EvidenceProvenance b = EvidenceProvenance.of(EvidenceFamily.HEALTH, "p", NOW, "r");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
