package com.madlanga.blastradius.telemetry.model;

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
                .service("storage-api")
                .endpoint("/actuator/health")
                .state(HealthState.UP)
                .httpStatus(200)
                .provenance(provenance("storage-api"))
                .build();

        assertTrue(h.isHealthy());
        assertFalse(h.isDegraded());
    }

    @Test
    void downStateIsDegraded() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-002")
                .timestamp(TS)
                .service("storage-api")
                .endpoint("/actuator/health/readiness")
                .state(HealthState.DOWN)
                .httpStatus(503)
                .provenance(provenance("storage-api"))
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
    void unknownStateIsFailureEvidenceWhenProbeWasAttempted() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-004")
                .timestamp(TS)
                .service("storage-api")
                .endpoint("/actuator/health")
                .state(HealthState.UNKNOWN)
                .provenance(provenance("storage-api"))
                .build();

        assertFalse(h.isHealthy());
        // UNKNOWN HealthEvidence means the probe was attempted but the component could not
        // be confirmed healthy. Provider-wide unavailability is represented by coverage,
        // not by fabricating a healthy observation.
        assertTrue(h.isDegraded());
    }

    @Test
    void preservesTimestamp() {
        Instant specific = Instant.parse("2026-10-01T10:31:06.999Z");
        HealthEvidence h = HealthEvidence.builder()
                .id("health-005")
                .timestamp(specific)
                .service("storage-api")
                .endpoint("/actuator/health")
                .state(HealthState.UP)
                .provenance(provenance("storage-api"))
                .build();
        assertEquals(specific, h.getTimestamp());
    }

    @Test
    void preservesLatencyMs() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-006")
                .timestamp(TS)
                .service("storage-api")
                .endpoint("/actuator/health")
                .state(HealthState.DOWN)
                .httpStatus(503)
                .latencyMs(4200L)
                .provenance(provenance("storage-api"))
                .build();
        assertEquals(4200L, h.getLatencyMs());
    }

    @Test
    void preservesDetails() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-007")
                .timestamp(TS)
                .service("storage-api")
                .endpoint("/actuator/health")
                .state(HealthState.DOWN)
                .httpStatus(503)
                .details(Map.of("db", "DOWN", "diskSpace", "UP"))
                .provenance(provenance("storage-api"))
                .build();

        assertEquals("DOWN", h.getDetails().get("db"));
        assertEquals("UP", h.getDetails().get("diskSpace"));
    }

    @Test
    void detailsAreImmutable() {
        HealthEvidence h = HealthEvidence.builder()
                .id("health-008")
                .timestamp(TS)
                .service("storage-api")
                .endpoint("/actuator/health")
                .state(HealthState.UP)
                .provenance(provenance("storage-api"))
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> h.getDetails().put("extra", "value"));
    }
}
