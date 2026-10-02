package com.madlanga.blastradius.domain.evidence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HealthEvidenceTest {

    private static final Instant TS = Instant.parse("2026-10-01T10:31:06Z");

    private EvidenceProvenance provenance(String service) {
        return EvidenceProvenance.of(EvidenceFamily.HEALTH, "local-actuator",
                Instant.now(), "http://" + service + ":8083/actuator/health");
    }

    @Test
    void upStateIsHealthy() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-001")
                .timestamp(TS)
                .service("document-service")
                .endpoint("/actuator/health")
                .state(HealthState.UP)
                .httpStatus(200)
                .provenance(provenance("document-service"))
                .build();

        assertTrue(h.isHealthy());
        assertFalse(h.isDegraded());
    }

    @Test
    void downStateIsDegraded() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-002")
                .timestamp(TS)
                .service("document-service")
                .endpoint("/actuator/health/readiness")
                .state(HealthState.DOWN)
                .httpStatus(503)
                .provenance(provenance("document-service"))
                .build();

        assertFalse(h.isHealthy());
        assertTrue(h.isDegraded());
    }

    @Test
    void degradedStateIsDegraded() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-003")
                .timestamp(TS)
                .service("some-service")
                .endpoint("/health")
                .state(HealthState.DEGRADED)
                .httpStatus(200)
                .provenance(provenance("some-service"))
                .build();

        assertFalse(h.isHealthy());
        assertTrue(h.isDegraded());
    }

    @Test
    void unknownStateIsNeitherHealthyNorDefinitelyDegraded() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-004")
                .timestamp(TS)
                .service("document-service")
                .endpoint("/actuator/health")
                .state(HealthState.UNKNOWN)
                .provenance(provenance("document-service"))
                .build();

        assertFalse(h.isHealthy());
        // UNKNOWN is not the same as DOWN/DEGRADED — represents missing information
        assertFalse(h.isDegraded());
    }

    @Test
    void preservesTimestamp() {
        Instant specific = Instant.parse("2026-10-01T10:31:06.999Z");
        HealthEvidence h = HealthEvidence.builder()
                .id("health-005")
                .timestamp(specific)
                .service("document-service")
                .endpoint("/actuator/health")
                .state(HealthState.UP)
                .provenance(provenance("document-service"))
                .build();
        assertEquals(specific, h.getTimestamp());
    }

    @Test
    void preservesLatencyMs() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-006")
                .timestamp(TS)
                .service("document-service")
                .endpoint("/actuator/health")
                .state(HealthState.DOWN)
                .httpStatus(503)
                .latencyMs(4200L)
                .provenance(provenance("document-service"))
                .build();
        assertEquals(4200L, h.getLatencyMs());
    }

    @Test
    void preservesDetails() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-007")
                .timestamp(TS)
                .service("document-service")
                .endpoint("/actuator/health")
                .state(HealthState.DOWN)
                .httpStatus(503)
                .details(Map.of("db", "DOWN", "diskSpace", "UP"))
                .provenance(provenance("document-service"))
                .build();

        assertEquals("DOWN", h.getDetails().get("db"));
        assertEquals("UP", h.getDetails().get("diskSpace"));
    }

    @Test
    void detailsAreImmutable() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-008")
                .timestamp(TS)
                .service("document-service")
                .endpoint("/actuator/health")
                .state(HealthState.UP)
                .provenance(provenance("document-service"))
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> h.getDetails().put("extra", "value"));
    }
}
