package com.madlanga.blastradius.domain.evidence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TelemetryBundle construction, coverage semantics, and the
 * critical invariant that UNAVAILABLE != healthy.
 */
class TelemetryBundleTest {

    private static final Instant TS = Instant.parse("2026-10-01T10:31:00Z");

    private EvidenceProvenance prov(EvidenceFamily family) {
        return EvidenceProvenance.of(family, "test-provider", Instant.now(), "test-ref");
    }

    private LogEvidence log(String service) {
        return LogEvidence.builder()
                .id("log-" + service)
                .timestamp(TS)
                .service(service)
                .level("INFO")
                .message("test message")
                .provenance(prov(EvidenceFamily.LOGS))
                .build();
    }

    private MetricEvidence metric(String service) {
        return MetricEvidence.builder()
                .id("metric-" + service)
                .timestamp(TS)
                .service(service)
                .name("http.server.requests.count")
                .value(10.0)
                .provenance(prov(EvidenceFamily.METRICS))
                .build();
    }

    private SpanEvidence span(String service) {
        return SpanEvidence.builder()
                .id("span-" + service)
                .traceId("trace001")
                .spanId("span001")
                .service(service)
                .startTime(TS)
                .status(SpanStatus.OK)
                .provenance(prov(EvidenceFamily.TRACES))
                .build();
    }

    private HealthEvidence health(String service, HealthState state) {
        return HealthEvidence.builder()
                .id("health-" + service)
                .timestamp(TS)
                .service(service)
                .endpoint("/actuator/health")
                .state(state)
                .httpStatus(state == HealthState.UP ? 200 : 503)
                .provenance(prov(EvidenceFamily.HEALTH))
                .build();
    }

    // -------------------------------------------------------------------------
    // Full bundle
    // -------------------------------------------------------------------------

    @Test
    void fullBundleIsFullyCovered() {
        TelemetryBundle bundle = TelemetryBundle.builder()
                .logs(List.of(log("document-service")))
                .metrics(List.of(metric("document-service")))
                .spans(List.of(span("document-service")))
                .health(List.of(health("document-service", HealthState.UP)))
                .coverage(TelemetryCoverage.allAvailable())
                .build();

        assertTrue(bundle.isFullyCovered());
        assertTrue(bundle.hasLogs());
        assertTrue(bundle.hasMetrics());
        assertTrue(bundle.hasSpans());
        assertTrue(bundle.hasHealthEvidence());
        assertFalse(bundle.hasWarnings());
    }

    // -------------------------------------------------------------------------
    // Partial bundle
    // -------------------------------------------------------------------------

    @Test
    void partialBundleReportsCorrectCoverage() {
        TelemetryCoverage coverage = TelemetryCoverage.builder()
                .logs(CoverageStatus.UNAVAILABLE)
                .metrics(CoverageStatus.AVAILABLE)
                .traces(CoverageStatus.UNAVAILABLE)
                .health(CoverageStatus.AVAILABLE)
                .build();

        TelemetryBundle bundle = TelemetryBundle.builder()
                .metrics(List.of(metric("payment-service")))
                .health(List.of(health("payment-service", HealthState.UP)))
                .coverage(coverage)
                .warning("Loki: provider unreachable")
                .warning("Tempo: provider unreachable")
                .build();

        assertFalse(bundle.isFullyCovered());
        assertFalse(bundle.hasLogs());
        assertFalse(bundle.hasSpans());
        assertTrue(bundle.hasMetrics());
        assertTrue(bundle.hasHealthEvidence());
        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getLogs());
        assertEquals(CoverageStatus.AVAILABLE, bundle.getCoverage().getMetrics());
        assertEquals(2, bundle.getWarnings().size());
    }

    // -------------------------------------------------------------------------
    // CRITICAL: UNAVAILABLE != healthy (ADR-017)
    // -------------------------------------------------------------------------

    @Test
    void unavailableLogsDoNotMeanServiceIsHealthy() {
        // This is the ADR-017 invariant: we cannot infer health from missing telemetry
        TelemetryBundle bundle = TelemetryBundle.unavailable("All providers unreachable");

        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getLogs());
        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getMetrics());
        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getTraces());
        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getHealth());

        // The bundle is empty — but that DOES NOT mean services are healthy
        // The caller must check coverage status, not infer from empty lists
        assertTrue(bundle.getLogs().isEmpty());
        assertTrue(bundle.getMetrics().isEmpty());
        assertTrue(bundle.getSpans().isEmpty());
        assertTrue(bundle.getHealth().isEmpty());

        // The warning explains WHY it's empty
        assertFalse(bundle.getWarnings().isEmpty());
    }

    @Test
    void notSupportedCoverageIsDistinctFromUnavailable() {
        TelemetryCoverage coverage = TelemetryCoverage.builder()
                .logs(CoverageStatus.NOT_SUPPORTED)
                .metrics(CoverageStatus.AVAILABLE)
                .traces(CoverageStatus.NOT_SUPPORTED)
                .health(CoverageStatus.AVAILABLE)
                .build();

        assertEquals(CoverageStatus.NOT_SUPPORTED, coverage.getLogs());
        // NOT_SUPPORTED means "this provider never supports it" vs UNAVAILABLE = "failed this time"
        assertNotEquals(CoverageStatus.UNAVAILABLE, coverage.getLogs());
    }

    @Test
    void allUnavailableHelperCreatesAllUnavailable() {
        TelemetryCoverage c = TelemetryCoverage.allUnavailable();
        assertEquals(CoverageStatus.UNAVAILABLE, c.getLogs());
        assertEquals(CoverageStatus.UNAVAILABLE, c.getMetrics());
        assertEquals(CoverageStatus.UNAVAILABLE, c.getTraces());
        assertEquals(CoverageStatus.UNAVAILABLE, c.getHealth());
        assertFalse(c.isFullyCovered());
        assertFalse(c.hasAnyEvidence());
    }

    @Test
    void allAvailableHelperCreatesFullCoverage() {
        TelemetryCoverage c = TelemetryCoverage.allAvailable();
        assertTrue(c.isFullyCovered());
        assertTrue(c.hasAnyEvidence());
    }

    // -------------------------------------------------------------------------
    // Warnings
    // -------------------------------------------------------------------------

    @Test
    void warningsAreCollectedFromBuilder() {
        TelemetryBundle bundle = TelemetryBundle.builder()
                .coverage(TelemetryCoverage.allAvailable())
                .warning("Loki returned fewer results than expected")
                .warning("Tempo: one trace fetch failed")
                .build();

        assertEquals(2, bundle.getWarnings().size());
        assertTrue(bundle.hasWarnings());
    }

    @Test
    void blankWarningsAreIgnored() {
        TelemetryBundle bundle = TelemetryBundle.builder()
                .coverage(TelemetryCoverage.allAvailable())
                .warning("")
                .warning("  ")
                .warning(null)
                .build();

        assertFalse(bundle.hasWarnings());
    }

    @Test
    void bundleListsAreImmutable() {
        TelemetryBundle bundle = TelemetryBundle.builder()
                .logs(List.of(log("document-service")))
                .coverage(TelemetryCoverage.allAvailable())
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> bundle.getLogs().add(log("payment-service")));
    }
}
