package com.madlanga.blastradius.domain.evidence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricEvidenceTest {

    private static final Instant TS = Instant.parse("2026-10-01T10:31:05Z");

    private EvidenceProvenance provenance() {
        return EvidenceProvenance.of(EvidenceFamily.METRICS, "local-prometheus",
                Instant.now(), "http_server_requests_seconds_count");
    }

    @Test
    void buildsWithRequiredFields() {
        MetricEvidence m = MetricEvidence.builder()
                .id("metric-001")
                .timestamp(TS)
                .service("document-service")
                .name("http.server.requests.seconds.count")
                .value(14.0)
                .provenance(provenance())
                .build();

        assertEquals("metric-001", m.getId());
        assertEquals(TS, m.getTimestamp());
        assertEquals("document-service", m.getService());
        assertEquals("http.server.requests.seconds.count", m.getName());
        assertEquals(14.0, m.getValue());
    }

    @Test
    void preservesTimestamp() {
        Instant specific = Instant.parse("2026-10-01T10:31:05.678Z");
        MetricEvidence m = MetricEvidence.builder()
                .id("metric-002")
                .timestamp(specific)
                .service("document-service")
                .name("http.server.requests.seconds.count")
                .value(0.0)
                .provenance(provenance())
                .build();
        assertEquals(specific, m.getTimestamp());
    }

    @Test
    void representsErrorRate() {
        MetricEvidence m = MetricEvidence.builder()
                .id("metric-003")
                .timestamp(TS)
                .service("document-service")
                .name("http.server.requests.seconds.count")
                .value(16.0)
                .unit("requests")
                .dimensions(Map.of("status", "503", "uri", "/api/documents"))
                .provenance(provenance())
                .build();

        assertEquals("503", m.getDimensions().get("status"));
        assertEquals("/api/documents", m.getDimensions().get("uri"));
    }

    @Test
    void representsLatency() {
        MetricEvidence m = MetricEvidence.builder()
                .id("metric-004")
                .timestamp(TS)
                .service("payment-service")
                .name("http.server.requests.seconds.sum")
                .value(115.095)
                .unit("seconds")
                .dimensions(Map.of("status", "502", "uri", "/api/payments"))
                .provenance(provenance())
                .build();

        assertEquals("seconds", m.getUnit());
    }

    @Test
    void representsHikariPoolMetric() {
        MetricEvidence m = MetricEvidence.builder()
                .id("metric-005")
                .timestamp(TS)
                .service("document-service")
                .name("hikaricp.connections.active")
                .value(0.0)
                .dimensions(Map.of("pool", "HikariPool-1"))
                .provenance(provenance())
                .build();

        assertEquals("hikaricp.connections.active", m.getName());
        assertEquals(0.0, m.getValue());
    }

    @Test
    void dimensionsAreImmutable() {
        MetricEvidence m = MetricEvidence.builder()
                .id("metric-006")
                .timestamp(TS)
                .service("document-service")
                .name("some.metric")
                .value(1.0)
                .provenance(provenance())
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> m.getDimensions().put("key", "value"));
    }
}
