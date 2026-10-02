package com.madlanga.blastradius.adapters.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.madlanga.blastradius.domain.evidence.CoverageStatus;
import com.madlanga.blastradius.domain.evidence.TelemetryBundle;
import com.madlanga.blastradius.domain.evidence.TelemetryQuery;
import com.madlanga.blastradius.ports.TelemetryProvider;

/**
 * Opt-in live integration test for the local Docker observability lab.
 *
 * <p>This test is deliberately excluded from normal Surefire discovery because
 * its class name ends in {@code IT}. Run it explicitly while the Docker Compose
 * lab is up. It exercises the real Spring wiring and all four provider adapters
 * without adding a public/debug HTTP endpoint.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "blast-radius.telemetry.loki.base-url=http://localhost:3100",
        "blast-radius.telemetry.prometheus.base-url=http://localhost:9090",
        "blast-radius.telemetry.tempo.base-url=http://localhost:3200",
        "blast-radius.telemetry.health.endpoints.blast-radius-api=http://localhost:8080",
        "blast-radius.telemetry.health.endpoints.payment-service=http://localhost:8081",
        "blast-radius.telemetry.health.endpoints.customer-service=http://localhost:8082",
        "blast-radius.telemetry.health.endpoints.document-service=http://localhost:8083"
})
class LocalTelemetryProviderLiveIT {

    @Autowired
    private TelemetryProvider telemetryProvider;

    @Test
    void retrievesNormalizedEvidenceFromRunningLocalLab() {
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofMinutes(15));

        TelemetryQuery query = TelemetryQuery.builder()
                .applicationId("document-platform")
                .environment("local")
                .from(from)
                .to(to)
                .build();

        TelemetryBundle bundle = telemetryProvider.getTelemetry(query);

        assertNotNull(bundle);
        assertNotNull(bundle.getCoverage());

        assertEquals(CoverageStatus.AVAILABLE, bundle.getCoverage().getLogs(),
                () -> "logs coverage: " + bundle.getWarnings());
        assertEquals(CoverageStatus.AVAILABLE, bundle.getCoverage().getMetrics(),
                () -> "metrics coverage: " + bundle.getWarnings());
        assertEquals(CoverageStatus.AVAILABLE, bundle.getCoverage().getTraces(),
                () -> "traces coverage: " + bundle.getWarnings());
        assertEquals(CoverageStatus.AVAILABLE, bundle.getCoverage().getHealth(),
                () -> "health coverage: " + bundle.getWarnings());

        assertFalse(bundle.getLogs().isEmpty(), "Expected normalized Loki log evidence");
        assertFalse(bundle.getMetrics().isEmpty(), "Expected normalized Prometheus metric evidence");
        assertFalse(bundle.getSpans().isEmpty(), "Expected normalized Tempo span evidence");
        assertFalse(bundle.getHealth().isEmpty(), "Expected normalized Actuator health evidence");

        assertTrue(bundle.getSpans().stream()
                        .filter(span -> span.getTraceId() != null)
                        .allMatch(span -> span.getTraceId().matches("[0-9a-f]{32}")),
                "Tempo trace IDs must be normalized to lowercase hexadecimal");

        assertTrue(bundle.getSpans().stream()
                        .filter(span -> span.getSpanId() != null)
                        .allMatch(span -> span.getSpanId().matches("[0-9a-f]{16}")),
                "Tempo span IDs must be normalized to lowercase hexadecimal");
    }
}
