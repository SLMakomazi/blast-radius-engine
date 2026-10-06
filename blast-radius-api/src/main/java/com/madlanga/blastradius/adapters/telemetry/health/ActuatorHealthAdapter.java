package com.madlanga.blastradius.adapters.telemetry.health;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.madlanga.blastradius.domain.evidence.CoverageStatus;
import com.madlanga.blastradius.domain.evidence.EvidenceFamily;
import com.madlanga.blastradius.domain.evidence.EvidenceProvenance;
import com.madlanga.blastradius.domain.evidence.HealthEvidence;
import com.madlanga.blastradius.domain.evidence.HealthState;
import com.madlanga.blastradius.domain.evidence.TelemetryQuery;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;

/**
 * Adapter that probes Spring Boot Actuator {@code /actuator/health} endpoints for
 * each configured component and maps responses to normalized {@link HealthEvidence}.
 *
 * <p>Probes all configured components independently. A failure to reach one component
 * is isolated — it does not suppress evidence from others (partial observability rule).
 * An unreachable endpoint produces a DOWN/UNKNOWN health observation, not silence.</p>
 *
 * <p>CRITICAL invariant: a HealthEvidence observation with state DOWN or UNKNOWN
 * is a real health signal. An UNAVAILABLE coverage status (adapter could not reach
 * any endpoint) does NOT mean all components are healthy.</p>
 *
 * <p>Component filtering: if the query contains a component filter, only matching
 * components are probed.</p>
 */
@Component
public class ActuatorHealthAdapter {

    private static final Logger log = LoggerFactory.getLogger(ActuatorHealthAdapter.class);

    private static final String HEALTH_PATH = "/actuator/health";
    private static final String LIVENESS_PATH = "/actuator/health/liveness";
    private static final String READINESS_PATH = "/actuator/health/readiness";

    private final ActuatorHealthProperties properties;
    private final TelemetrySanitizer sanitizer;
    private final RestClient restClient;

    public ActuatorHealthAdapter(ActuatorHealthProperties properties,
                                  TelemetrySanitizer sanitizer,
                                  RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.sanitizer = sanitizer;
        // No base URL — each probe uses the component-specific URL
        this.restClient = restClientBuilder.build();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public HealthAdapterResult fetchHealth(TelemetryQuery query) {
        List<HealthEvidence> allHealth = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int probeCount = 0;
        int successCount = 0;

        for (Map.Entry<String, String> entry : properties.getEndpoints().entrySet()) {
            String componentName = entry.getKey();
            String baseUrl = entry.getValue();

            // Apply component filter if specified
            if (query.hasComponentFilter()
                    && !query.getComponentFilter().contains(componentName)) {
                continue;
            }

            probeCount++;
            // A live probe is collected while answering this query. Stamp it at the
            // query boundary so IncidentAnalysisService's [from,to) window does not
            // discard the observation merely because the HTTP probe completed after
            // query.getTo() was captured.
            Instant probeTime = query.getTo().minusNanos(1);

            // Probe /actuator/health (full + liveness + readiness where available)
            HealthProbeResult rootResult = probeEndpoint(
                    componentName, baseUrl, HEALTH_PATH,
                    query.getEnvironment(), probeTime);
            allHealth.add(rootResult.evidence);
            if (rootResult.warning != null) warnings.add(rootResult.warning);
            if (rootResult.success) successCount++;

            // Also probe readiness separately — document-service readiness reflects DB
            HealthProbeResult readinessResult = probeEndpoint(
                    componentName, baseUrl, READINESS_PATH,
                    query.getEnvironment(), probeTime);
            allHealth.add(readinessResult.evidence);
            if (readinessResult.warning != null) warnings.add(readinessResult.warning);
        }

        CoverageStatus coverage;
        if (probeCount == 0) {
            coverage = CoverageStatus.UNAVAILABLE;
            warnings.add("Health: no components matched the query filter.");
        } else if (successCount == 0) {
            coverage = CoverageStatus.UNAVAILABLE;
        } else if (successCount < probeCount) {
            coverage = CoverageStatus.PARTIAL;
        } else {
            coverage = CoverageStatus.AVAILABLE;
        }

        return new HealthAdapterResult(allHealth, coverage, warnings);
    }

    // -------------------------------------------------------------------------
    // Per-endpoint probe
    // -------------------------------------------------------------------------

    private HealthProbeResult probeEndpoint(String componentName,
                                             String baseUrl,
                                             String path,
                                             String environment,
                                             Instant probeTime) {
        Instant start = Instant.now();
        String sourceRef = baseUrl + path;

        EvidenceProvenance provenance = EvidenceProvenance.of(
                EvidenceFamily.HEALTH,
                properties.getProviderId(),
                probeTime,
                sourceRef);

        try {
            long[] httpStatusHolder = {200};
            ActuatorHealthResponse response = restClient.get()
                    .uri(baseUrl + path)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        httpStatusHolder[0] = resp.getStatusCode().value();
                        // Don't throw — a non-2xx Actuator response is still evidence
                    })
                    .body(ActuatorHealthResponse.class);

            long latencyMs = Instant.now().toEpochMilli() - start.toEpochMilli();

            if (response == null) {
                HealthEvidence evidence = buildEvidence(componentName, path, environment,
                        probeTime, HealthState.UNKNOWN, (int) httpStatusHolder[0],
                        latencyMs, Map.of(), provenance);
                return new HealthProbeResult(evidence, true, null);
            }

            HealthState state = mapHealthState(response.status, (int) httpStatusHolder[0]);
            Map<String, String> details = buildDetails(response);
            Map<String, String> sanitizedDetails = sanitizer.sanitizeAttributes(details);

            HealthEvidence evidence = buildEvidence(componentName, path, environment,
                    probeTime, state, (int) httpStatusHolder[0],
                    latencyMs, sanitizedDetails, provenance);
            return new HealthProbeResult(evidence, true, null);

        } catch (RestClientResponseException e) {
            long latencyMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            // HTTP error response — this IS health evidence (the service is down/degraded)
            HealthState state = mapHealthState(null, e.getStatusCode().value());
            HealthEvidence evidence = buildEvidence(componentName, path, environment,
                    probeTime, state, e.getStatusCode().value(),
                    latencyMs, Map.of(), provenance);
            return new HealthProbeResult(evidence, true, null);

        } catch (RestClientException e) {
            long latencyMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            // Connection failure — component unreachable; report UNKNOWN not healthy
            log.warn("Health adapter: could not reach {} at {} [{}]",
                    componentName, path, e.getClass().getSimpleName());
            HealthEvidence evidence = buildEvidence(componentName, path, environment,
                    probeTime, HealthState.UNKNOWN, null,
                    latencyMs, Map.of(), provenance);
            String warning = "Health: could not reach " + componentName + " at " + path
                    + " — connection failed";
            return new HealthProbeResult(evidence, false, warning);
        }
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private static HealthState mapHealthState(String statusText, int httpStatus) {
        // HTTP 503 from Actuator means DOWN (e.g. document readiness with DB failed)
        if (httpStatus == 503) return HealthState.DOWN;
        if (httpStatus >= 500) return HealthState.DOWN;
        if (httpStatus == 404) return HealthState.UNKNOWN;
        if (httpStatus >= 400) return HealthState.UNKNOWN;

        if (statusText == null) return HealthState.UNKNOWN;
        return switch (statusText.toUpperCase()) {
            case "UP" -> HealthState.UP;
            case "DOWN" -> HealthState.DOWN;
            case "OUT_OF_SERVICE" -> HealthState.DOWN;
            case "RESTRICTED" -> HealthState.DEGRADED;
            default -> HealthState.UNKNOWN;
        };
    }

    private static Map<String, String> buildDetails(ActuatorHealthResponse response) {
        Map<String, String> details = new LinkedHashMap<>();
        if (response.components != null) {
            for (Map.Entry<String, ActuatorHealthResponse.ComponentHealth> entry
                    : response.components.entrySet()) {
                if (entry.getValue() != null && entry.getValue().status != null) {
                    details.put(entry.getKey(), entry.getValue().status);
                }
            }
        }
        return details;
    }

    private static HealthEvidence buildEvidence(String service, String endpoint,
                                                 String environment, Instant timestamp,
                                                 HealthState state, Integer httpStatus,
                                                 long latencyMs, Map<String, String> details,
                                                 EvidenceProvenance provenance) {
        return HealthEvidence.builder()
                .id("health-" + UUID.randomUUID())
                .timestamp(timestamp)
                .service(service)
                .environment(environment)
                .endpoint(endpoint)
                .state(state)
                .httpStatus(httpStatus)
                .latencyMs(latencyMs)
                .details(details)
                .provenance(provenance)
                .build();
    }

    // -------------------------------------------------------------------------
    // Internal result and probe containers
    // -------------------------------------------------------------------------

    private record HealthProbeResult(HealthEvidence evidence, boolean success, String warning) {}

    public static final class HealthAdapterResult {
        private final List<HealthEvidence> health;
        private final CoverageStatus coverage;
        private final List<String> warnings;

        public HealthAdapterResult(List<HealthEvidence> health,
                            CoverageStatus coverage,
                            List<String> warnings) {
            this.health = health;
            this.coverage = coverage;
            this.warnings = warnings;
        }

        /** Factory: UNAVAILABLE result with a single explanatory warning. */
        public static HealthAdapterResult unavailable(String warning) {
            return new HealthAdapterResult(List.of(), CoverageStatus.UNAVAILABLE, List.of(warning));
        }

        public List<HealthEvidence> getHealth() { return health; }
        public CoverageStatus getCoverage() { return coverage; }
        public List<String> getWarnings() { return warnings; }
    }
}
