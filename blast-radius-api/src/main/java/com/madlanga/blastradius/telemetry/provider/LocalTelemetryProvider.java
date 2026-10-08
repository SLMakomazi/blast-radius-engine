package com.madlanga.blastradius.telemetry.provider;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.madlanga.blastradius.telemetry.provider.health.ActuatorHealthAdapter;
import com.madlanga.blastradius.telemetry.provider.health.DatabaseHealthAdapter;
import com.madlanga.blastradius.telemetry.provider.loki.LokiLogAdapter;
import com.madlanga.blastradius.telemetry.provider.prometheus.PrometheusMetricsAdapter;
import com.madlanga.blastradius.telemetry.provider.tempo.TempoTraceAdapter;
import com.madlanga.blastradius.telemetry.model.TelemetryBundle;
import com.madlanga.blastradius.telemetry.model.TelemetryCoverage;
import com.madlanga.blastradius.telemetry.model.CoverageStatus;
import com.madlanga.blastradius.telemetry.model.TelemetryQuery;
import com.madlanga.blastradius.telemetry.provider.TelemetryProvider;

/**
 * Composite {@link TelemetryProvider} that aggregates the four local provider adapters
 * (Loki, Prometheus, Tempo, Actuator health) into a single normalized
 * {@link TelemetryBundle}.
 *
 * <p>Isolation guarantee: each adapter is called independently. A failure in one
 * adapter is caught and recorded as a warning and a coverage gap; it does NOT
 * prevent the other adapters from contributing evidence. This implements the
 * partial-observability contract defined in ADR-012 and ADR-017.</p>
 *
 * <p>Architecture position:
 * <pre>
 *   TelemetryProvider (port)
 *          ↑
 *   LocalTelemetryProvider  ← this class (composite, Spring @Component)
 *    /        |        |        \
 *   Loki  Prometheus  Tempo  Actuator
 * </pre>
 *
 * <p>The domain and service layers depend only on {@link TelemetryProvider}.
 * They never reference {@code LocalTelemetryProvider} or any adapter class
 * directly, so a future Datadog or other provider replaces this implementation
 * without touching the domain.</p>
 *
 * <p>Coverage invariant: UNAVAILABLE for any family does NOT mean the
 * corresponding services are healthy (ADR-017).</p>
 */
@Component
public class LocalTelemetryProvider implements TelemetryProvider {

    @org.springframework.beans.factory.annotation.Value("${blast-radius.topology.application-id:}")
    private String configuredApplication = "";
    @org.springframework.beans.factory.annotation.Value("${blast-radius.topology.environment:local}")
    private String configuredEnvironment = "local";
    private static final Logger log = LoggerFactory.getLogger(LocalTelemetryProvider.class);

    private final LokiLogAdapter lokiAdapter;
    private final PrometheusMetricsAdapter prometheusAdapter;
    private final TempoTraceAdapter tempoAdapter;
    private final ActuatorHealthAdapter healthAdapter;
    private final DatabaseHealthAdapter databaseHealthAdapter;

    public LocalTelemetryProvider(LokiLogAdapter lokiAdapter,
                                   PrometheusMetricsAdapter prometheusAdapter,
                                   TempoTraceAdapter tempoAdapter,
                                   ActuatorHealthAdapter healthAdapter,
                                   DatabaseHealthAdapter databaseHealthAdapter) {
        this.lokiAdapter = lokiAdapter;
        this.prometheusAdapter = prometheusAdapter;
        this.tempoAdapter = tempoAdapter;
        this.healthAdapter = healthAdapter;
        this.databaseHealthAdapter = databaseHealthAdapter;
    }

    /**
     * Retrieve all available normalized telemetry from the local lab providers.
     *
     * <p>Each adapter is called in sequence. Failures are isolated: an adapter
     * exception is caught here as a last-resort safety net (adapters are also
     * expected to catch internally), and the bundle is still built from whatever
     * evidence was collected.</p>
     *
     * @param query provider-neutral query
     * @return fully populated TelemetryBundle; never null
     */
    @Override
    public TelemetryBundle getTelemetry(TelemetryQuery query) {
        if (!configuredApplication.isBlank() && (!configuredApplication.equals(query.getApplicationId()) || !configuredEnvironment.equals(query.getEnvironment())))
            throw new IllegalArgumentException("Application/environment is outside the configured monitoring profile");
        List<String> allWarnings = new ArrayList<>();

        // ----- Logs (Loki) -----
        LokiLogAdapter.LogAdapterResult logsResult = fetchLogs(query, allWarnings);

        // ----- Metrics (Prometheus) -----
        PrometheusMetricsAdapter.MetricAdapterResult metricsResult = fetchMetrics(query, allWarnings);

        // ----- Spans (Tempo) -----
        TempoTraceAdapter.TraceAdapterResult spansResult = fetchSpans(query, allWarnings);

        // ----- Health (Actuator) -----
        ActuatorHealthAdapter.HealthAdapterResult healthResult = fetchHealth(query, allWarnings);
        var healthEvidence = new ArrayList<>(healthResult.getHealth());
        var databaseHealth = databaseHealthAdapter.fetchHealth(query);
        if (databaseHealth != null) healthEvidence.add(databaseHealth);

        CoverageStatus healthCoverage = healthResult.getCoverage();
        if (databaseHealth != null) {
            boolean databaseProbeConclusive = databaseHealth.getState() != com.madlanga.blastradius.telemetry.model.HealthState.UNKNOWN;
            if (healthCoverage == CoverageStatus.UNAVAILABLE && healthResult.getHealth().isEmpty()) {
                healthCoverage = databaseProbeConclusive ? CoverageStatus.AVAILABLE : CoverageStatus.UNAVAILABLE;
            } else if (!databaseProbeConclusive && healthCoverage == CoverageStatus.AVAILABLE) {
                healthCoverage = CoverageStatus.PARTIAL;
            }
        }

        TelemetryCoverage coverage = TelemetryCoverage.builder()
                .logs(logsResult.getCoverage())
                .metrics(metricsResult.getCoverage())
                .traces(spansResult.getCoverage())
                .health(healthCoverage)
                .build();

        log.debug("TelemetryBundle assembled for {}/{}: logs={}, metrics={}, spans={}, health={}, warnings={}",
                query.getApplicationId(), query.getEnvironment(),
                logsResult.getLogs().size(),
                metricsResult.getMetrics().size(),
                spansResult.getSpans().size(),
                healthResult.getHealth().size(),
                allWarnings.size());

        return TelemetryBundle.builder()
                .logs(logsResult.getLogs())
                .metrics(metricsResult.getMetrics())
                .spans(spansResult.getSpans())
                .health(healthEvidence)
                .coverage(coverage)
                .warnings(allWarnings)
                .build();
    }

    // -------------------------------------------------------------------------
    // Isolated per-adapter calls — each wrapped in its own try/catch so that
    // an unchecked exception escaping an adapter does not suppress other families.
    //
    // SECURITY: catch blocks must NEVER log or expose raw exception messages,
    // exception stack traces, or e.getMessage(). Provider exceptions can contain
    // URLs, query strings, telemetry values, credentials or tokens. Only the
    // exception class category and a fixed failure description are exposed.
    // -------------------------------------------------------------------------

    private LokiLogAdapter.LogAdapterResult fetchLogs(TelemetryQuery query,
                                                       List<String> warnings) {
        try {
            LokiLogAdapter.LogAdapterResult result = lokiAdapter.fetchLogs(query);
            warnings.addAll(result.getWarnings());
            return result;
        } catch (Exception e) {
            String warning = providerFailureWarning("logs", "Loki", e);
            log.error("LocalTelemetryProvider: unhandled exception from Loki adapter [{}]",
                    e.getClass().getSimpleName());
            warnings.add(warning);
            return LokiLogAdapter.LogAdapterResult.unavailable(warning);
        }
    }

    private PrometheusMetricsAdapter.MetricAdapterResult fetchMetrics(TelemetryQuery query,
                                                                        List<String> warnings) {
        try {
            PrometheusMetricsAdapter.MetricAdapterResult result = prometheusAdapter.fetchMetrics(query);
            warnings.addAll(result.getWarnings());
            return result;
        } catch (Exception e) {
            String warning = providerFailureWarning("metrics", "Prometheus", e);
            log.error("LocalTelemetryProvider: unhandled exception from Prometheus adapter [{}]",
                    e.getClass().getSimpleName());
            warnings.add(warning);
            return PrometheusMetricsAdapter.MetricAdapterResult.unavailable(warning);
        }
    }

    private TempoTraceAdapter.TraceAdapterResult fetchSpans(TelemetryQuery query,
                                                             List<String> warnings) {
        try {
            TempoTraceAdapter.TraceAdapterResult result = tempoAdapter.fetchSpans(query);
            warnings.addAll(result.getWarnings());
            return result;
        } catch (Exception e) {
            String warning = providerFailureWarning("traces", "Tempo", e);
            log.error("LocalTelemetryProvider: unhandled exception from Tempo adapter [{}]",
                    e.getClass().getSimpleName());
            warnings.add(warning);
            return TempoTraceAdapter.TraceAdapterResult.unavailable(warning);
        }
    }

    private ActuatorHealthAdapter.HealthAdapterResult fetchHealth(TelemetryQuery query,
                                                                   List<String> warnings) {
        try {
            ActuatorHealthAdapter.HealthAdapterResult result = healthAdapter.fetchHealth(query);
            warnings.addAll(result.getWarnings());
            return result;
        } catch (Exception e) {
            String warning = providerFailureWarning("health", "Actuator", e);
            log.error("LocalTelemetryProvider: unhandled exception from Health adapter [{}]",
                    e.getClass().getSimpleName());
            warnings.add(warning);
            return ActuatorHealthAdapter.HealthAdapterResult.unavailable(warning);
        }
    }

    /**
     * Produce a safe, fixed warning string for an unhandled adapter exception.
     *
     * <p>Only the evidence family name, provider name, and exception class simple
     * name are included. The raw exception message is deliberately excluded because
     * it may contain provider URLs, query parameters, credentials, telemetry values
     * or other sensitive information.</p>
     */
    static String providerFailureWarning(String family, String providerName, Exception e) {
        return family + ": unexpected provider failure from " + providerName
                + " [" + e.getClass().getSimpleName() + "]";
    }
}
