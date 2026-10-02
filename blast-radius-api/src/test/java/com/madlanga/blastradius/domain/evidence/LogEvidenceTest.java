package com.madlanga.blastradius.domain.evidence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogEvidenceTest {

    private static final Instant TS = Instant.parse("2026-10-01T10:31:02Z");

    private EvidenceProvenance provenance() {
        return EvidenceProvenance.of(EvidenceFamily.LOGS, "local-loki", Instant.now(), "stream");
    }

    @Test
    void buildsWithRequiredFields() {
        LogEvidence e = LogEvidence.builder()
                .id("log-001")
                .timestamp(TS)
                .service("document-service")
                .provenance(provenance())
                .build();

        assertEquals("log-001", e.getId());
        assertEquals(TS, e.getTimestamp());
        assertEquals("document-service", e.getService());
        assertNotNull(e.getProvenance());
    }

    @Test
    void preservesCorrelationId() {
        LogEvidence e = LogEvidence.builder()
                .id("log-001")
                .timestamp(TS)
                .service("payment-service")
                .correlationId("phase3-healthy-abc")
                .provenance(provenance())
                .build();

        assertEquals("phase3-healthy-abc", e.getCorrelationId());
    }

    @Test
    void preservesTraceAndSpanIds() {
        LogEvidence e = LogEvidence.builder()
                .id("log-002")
                .timestamp(TS)
                .service("customer-service")
                .traceId("61e1c07146fcb6829b35fca26be213c3")
                .spanId("4a627d450e97f6bf")
                .provenance(provenance())
                .build();

        assertEquals("61e1c07146fcb6829b35fca26be213c3", e.getTraceId());
        assertEquals("4a627d450e97f6bf", e.getSpanId());
    }

    @Test
    void preservesTimestamp() {
        // Timestamp must survive the builder unchanged — no normalisation
        Instant specific = Instant.parse("2026-10-01T10:31:02.123456789Z");
        LogEvidence e = LogEvidence.builder()
                .id("log-003")
                .timestamp(specific)
                .service("document-service")
                .provenance(provenance())
                .build();
        assertEquals(specific, e.getTimestamp());
    }

    @Test
    void preservesSanitizedAttributes() {
        Map<String, String> attrs = Map.of("error.type", "ConnectionException");
        LogEvidence e = LogEvidence.builder()
                .id("log-004")
                .timestamp(TS)
                .service("document-service")
                .attributes(attrs)
                .provenance(provenance())
                .build();

        assertEquals("ConnectionException", e.getAttributes().get("error.type"));
    }

    @Test
    void attributesAreImmutable() {
        LogEvidence e = LogEvidence.builder()
                .id("log-005")
                .timestamp(TS)
                .service("document-service")
                .provenance(provenance())
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> e.getAttributes().put("new-key", "value"));
    }

    @Test
    void rejectsNullId() {
        assertThrows(NullPointerException.class, () ->
                LogEvidence.builder()
                        .timestamp(TS)
                        .service("document-service")
                        .provenance(provenance())
                        .build());
    }
}
