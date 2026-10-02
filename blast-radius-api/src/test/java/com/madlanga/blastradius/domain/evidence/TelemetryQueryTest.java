package com.madlanga.blastradius.domain.evidence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryQueryTest {

    private static final Instant FROM = Instant.parse("2026-10-01T10:30:00Z");
    private static final Instant TO   = Instant.parse("2026-10-01T10:35:00Z");

    @Test
    void buildsWithRequiredFields() {
        TelemetryQuery q = TelemetryQuery.builder()
                .applicationId("document-platform")
                .environment("local")
                .from(FROM)
                .to(TO)
                .build();

        assertEquals("document-platform", q.getApplicationId());
        assertEquals("local", q.getEnvironment());
        assertEquals(FROM, q.getFrom());
        assertEquals(TO, q.getTo());
        assertFalse(q.hasComponentFilter());
        assertFalse(q.hasCorrelationId());
        assertFalse(q.hasTraceId());
    }

    @Test
    void buildsWithOptionalFilters() {
        TelemetryQuery q = TelemetryQuery.builder()
                .applicationId("app")
                .environment("local")
                .from(FROM)
                .to(TO)
                .componentFilter(List.of("payment-service", "customer-service"))
                .correlationId("phase3-healthy-abc")
                .traceId("61e1c07146fcb6829b35fca26be213c3")
                .build();

        assertTrue(q.hasComponentFilter());
        assertTrue(q.hasCorrelationId());
        assertTrue(q.hasTraceId());
        assertEquals(2, q.getComponentFilter().size());
    }

    @Test
    void rejectsFromAfterTo() {
        assertThrows(IllegalArgumentException.class, () ->
                TelemetryQuery.builder()
                        .applicationId("app")
                        .environment("local")
                        .from(TO)
                        .to(FROM)
                        .build());
    }

    @Test
    void rejectsFromEqualToTo() {
        assertThrows(IllegalArgumentException.class, () ->
                TelemetryQuery.builder()
                        .applicationId("app")
                        .environment("local")
                        .from(FROM)
                        .to(FROM)
                        .build());
    }

    @Test
    void rejectsNullApplicationId() {
        assertThrows(NullPointerException.class, () ->
                TelemetryQuery.builder()
                        .environment("local")
                        .from(FROM)
                        .to(TO)
                        .build());
    }
}
