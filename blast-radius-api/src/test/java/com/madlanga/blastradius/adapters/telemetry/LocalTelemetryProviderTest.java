package com.madlanga.blastradius.adapters.telemetry;

import com.madlanga.blastradius.adapters.telemetry.health.ActuatorHealthAdapter;
import com.madlanga.blastradius.adapters.telemetry.loki.LokiLogAdapter;
import com.madlanga.blastradius.adapters.telemetry.prometheus.PrometheusMetricsAdapter;
import com.madlanga.blastradius.adapters.telemetry.tempo.TempoTraceAdapter;
import com.madlanga.blastradius.domain.evidence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the composite {@link LocalTelemetryProvider}, focusing on:
 * - full bundle assembly;
 * - partial observability (one or more families unavailable);
 * - provider failure isolation;
 * - the UNAVAILABLE != healthy invariant.
 */
@ExtendWith(MockitoExtension.class)
class LocalTelemetryProviderTest {

    @Mock
    private LokiLogAdapter lokiAdapter;

    @Mock
    private PrometheusMetricsAdapter prometheusAdapter;

    @Mock
    private TempoTraceAdapter tempoAdapter;

    @Mock
    private ActuatorHealthAdapter healthAdapter;

    private LocalTelemetryProvider provider;

    private static final Instant FROM = Instant.parse("2026-10-01T10:30:00Z");
    private static final Instant TO   = Instant.parse("2026-10-01T10:35:00Z");

    @BeforeEach
    void setUp() {
        provider = new LocalTelemetryProvider(lokiAdapter, prometheusAdapter,
                tempoAdapter, healthAdapter);
    }

    private TelemetryQuery query() {
        return TelemetryQuery.builder()
                .applicationId("document-platform")
                .environment("local")
                .from(FROM)
                .to(TO)
                .build();
    }

    private EvidenceProvenance prov(EvidenceFamily family) {
        return EvidenceProvenance.of(family, "test", Instant.now(), "ref");
    }

    private LogEvidence sampleLog() {
        return LogEvidence.builder().id("log-1").timestamp(FROM)
                .service("document-service").level("ERROR")
                .message("event=dependency_failed")
                .provenance(prov(EvidenceFamily.LOGS)).build();
    }

    private MetricEvidence sampleMetric() {
        return MetricEvidence.builder().id("metric-1").timestamp(FROM)
                .service("document-service").name("http.server.requests.count")
                .value(16.0).provenance(prov(EvidenceFamily.METRICS)).build();
    }

    private SpanEvidence sampleSpan() {
        return SpanEvidence.builder().id("span-1").traceId("trace001")
                .spanId("span001").service("document-service").startTime(FROM)
                .status(SpanStatus.ERROR).provenance(prov(EvidenceFamily.TRACES)).build();
    }

    private HealthEvidence sampleHealth(HealthState state) {
        return HealthEvidence.builder().id("health-1").timestamp(FROM)
                .service("document-service").endpoint("/actuator/health")
                .state(state).httpStatus(state == HealthState.UP ? 200 : 503)
                .provenance(prov(EvidenceFamily.HEALTH)).build();
    }

    // -------------------------------------------------------------------------
    // Full bundle
    // -------------------------------------------------------------------------

    @Test
    void assemblesFullBundleWhenAllProvidersSucceed() {
        when(lokiAdapter.fetchLogs(any())).thenReturn(
                new LokiLogAdapter.LogAdapterResult(List.of(sampleLog()),
                        CoverageStatus.AVAILABLE, List.of()));
        when(prometheusAdapter.fetchMetrics(any())).thenReturn(
                new PrometheusMetricsAdapter.MetricAdapterResult(List.of(sampleMetric()),
                        CoverageStatus.AVAILABLE, List.of()));
        when(tempoAdapter.fetchSpans(any())).thenReturn(
                new TempoTraceAdapter.TraceAdapterResult(List.of(sampleSpan()),
                        CoverageStatus.AVAILABLE, List.of()));
        when(healthAdapter.fetchHealth(any())).thenReturn(
                new ActuatorHealthAdapter.HealthAdapterResult(List.of(sampleHealth(HealthState.UP)),
                        CoverageStatus.AVAILABLE, List.of()));

        TelemetryBundle bundle = provider.getTelemetry(query());

        assertTrue(bundle.isFullyCovered());
        assertEquals(1, bundle.getLogs().size());
        assertEquals(1, bundle.getMetrics().size());
        assertEquals(1, bundle.getSpans().size());
        assertEquals(1, bundle.getHealth().size());
        assertFalse(bundle.hasWarnings());
    }

    // -------------------------------------------------------------------------
    // Partial observability
    // -------------------------------------------------------------------------

    @Test
    void logsUnavailableDoesNotSuppressOtherFamilies() {
        when(lokiAdapter.fetchLogs(any())).thenReturn(
                LokiLogAdapter.LogAdapterResult.unavailable("Loki unreachable"));
        when(prometheusAdapter.fetchMetrics(any())).thenReturn(
                new PrometheusMetricsAdapter.MetricAdapterResult(List.of(sampleMetric()),
                        CoverageStatus.AVAILABLE, List.of()));
        when(tempoAdapter.fetchSpans(any())).thenReturn(
                TempoTraceAdapter.TraceAdapterResult.unavailable("Tempo unreachable"));
        when(healthAdapter.fetchHealth(any())).thenReturn(
                new ActuatorHealthAdapter.HealthAdapterResult(
                        List.of(sampleHealth(HealthState.UP)),
                        CoverageStatus.AVAILABLE, List.of()));

        TelemetryBundle bundle = provider.getTelemetry(query());

        assertFalse(bundle.isFullyCovered());
        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getLogs());
        assertEquals(CoverageStatus.AVAILABLE,   bundle.getCoverage().getMetrics());
        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getTraces());
        assertEquals(CoverageStatus.AVAILABLE,   bundle.getCoverage().getHealth());
        assertTrue(bundle.hasMetrics());
        assertTrue(bundle.hasHealthEvidence());
        assertFalse(bundle.hasLogs());
        assertFalse(bundle.hasSpans());
        assertTrue(bundle.hasWarnings());
    }

    @Test
    void allProvidersUnavailableProducesAllUnavailableWithWarnings() {
        when(lokiAdapter.fetchLogs(any())).thenReturn(
                LokiLogAdapter.LogAdapterResult.unavailable("Loki down"));
        when(prometheusAdapter.fetchMetrics(any())).thenReturn(
                PrometheusMetricsAdapter.MetricAdapterResult.unavailable("Prometheus down"));
        when(tempoAdapter.fetchSpans(any())).thenReturn(
                TempoTraceAdapter.TraceAdapterResult.unavailable("Tempo down"));
        when(healthAdapter.fetchHealth(any())).thenReturn(
                ActuatorHealthAdapter.HealthAdapterResult.unavailable("Health down"));

        TelemetryBundle bundle = provider.getTelemetry(query());

        assertFalse(bundle.isFullyCovered());
        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getLogs());
        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getMetrics());
        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getTraces());
        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getHealth());

        // CRITICAL: empty bundle != healthy services
        assertTrue(bundle.getLogs().isEmpty());
        assertTrue(bundle.getMetrics().isEmpty());
        assertTrue(bundle.getSpans().isEmpty());
        assertTrue(bundle.getHealth().isEmpty());
        assertTrue(bundle.hasWarnings()); // Caller must see explicit explanation
    }

    // -------------------------------------------------------------------------
    // Provider failure isolation
    // -------------------------------------------------------------------------

    @Test
    void unhandledExceptionFromLokiIsIsolated() {
        // Adapter throws unchecked exception — composite must catch and isolate
        when(lokiAdapter.fetchLogs(any())).thenThrow(
                new RuntimeException("Unexpected Loki failure"));
        when(prometheusAdapter.fetchMetrics(any())).thenReturn(
                new PrometheusMetricsAdapter.MetricAdapterResult(List.of(sampleMetric()),
                        CoverageStatus.AVAILABLE, List.of()));
        when(tempoAdapter.fetchSpans(any())).thenReturn(
                new TempoTraceAdapter.TraceAdapterResult(List.of(sampleSpan()),
                        CoverageStatus.AVAILABLE, List.of()));
        when(healthAdapter.fetchHealth(any())).thenReturn(
                new ActuatorHealthAdapter.HealthAdapterResult(
                        List.of(sampleHealth(HealthState.DOWN)),
                        CoverageStatus.AVAILABLE, List.of()));

        TelemetryBundle bundle = provider.getTelemetry(query());

        // The composite must NOT propagate the Loki exception
        assertNotNull(bundle);
        assertEquals(CoverageStatus.UNAVAILABLE, bundle.getCoverage().getLogs());
        assertEquals(CoverageStatus.AVAILABLE, bundle.getCoverage().getMetrics());
        assertEquals(CoverageStatus.AVAILABLE, bundle.getCoverage().getTraces());
        assertEquals(CoverageStatus.AVAILABLE, bundle.getCoverage().getHealth());
        assertTrue(bundle.hasWarnings());
    }

    // -------------------------------------------------------------------------
    // UNAVAILABLE != healthy (ADR-017)
    // -------------------------------------------------------------------------

    @Test
    void missingEvidenceIsNotInterpretedAsHealthy() {
        // All families unavailable — the bundle must make this explicit
        when(lokiAdapter.fetchLogs(any())).thenReturn(
                LokiLogAdapter.LogAdapterResult.unavailable("no logs"));
        when(prometheusAdapter.fetchMetrics(any())).thenReturn(
                PrometheusMetricsAdapter.MetricAdapterResult.unavailable("no metrics"));
        when(tempoAdapter.fetchSpans(any())).thenReturn(
                TempoTraceAdapter.TraceAdapterResult.unavailable("no spans"));
        when(healthAdapter.fetchHealth(any())).thenReturn(
                ActuatorHealthAdapter.HealthAdapterResult.unavailable("no health"));

        TelemetryBundle bundle = provider.getTelemetry(query());

        // The coverage object explicitly marks everything as UNAVAILABLE
        // A caller MUST inspect coverage — not infer from empty lists
        assertFalse(bundle.getCoverage().hasAnyEvidence());

        // Warnings are present so the caller knows why evidence is missing
        assertTrue(bundle.hasWarnings(),
                "Missing evidence must produce warnings — not silent empty results");
    }

    @Test
    void neverReturnsNullWhenAllEvidenceFamiliesAreUnavailable() {
        when(lokiAdapter.fetchLogs(any())).thenReturn(
                LokiLogAdapter.LogAdapterResult.unavailable("logs unavailable"));
        when(prometheusAdapter.fetchMetrics(any())).thenReturn(
                PrometheusMetricsAdapter.MetricAdapterResult.unavailable("metrics unavailable"));
        when(tempoAdapter.fetchSpans(any())).thenReturn(
                TempoTraceAdapter.TraceAdapterResult.unavailable("traces unavailable"));
        when(healthAdapter.fetchHealth(any())).thenReturn(
                ActuatorHealthAdapter.HealthAdapterResult.unavailable("health unavailable"));

        TelemetryBundle bundle = provider.getTelemetry(query());

        assertNotNull(bundle,
                "TelemetryProvider must always return a bundle for ordinary provider unavailability");
        assertNotNull(bundle.getCoverage(),
                "TelemetryBundle must explicitly describe evidence coverage");
        assertFalse(bundle.getCoverage().hasAnyEvidence(),
                "Unavailable evidence must not be interpreted as healthy or available");
        assertTrue(bundle.hasWarnings(),
                "Unavailable evidence families must produce explicit warnings");
    }
}
