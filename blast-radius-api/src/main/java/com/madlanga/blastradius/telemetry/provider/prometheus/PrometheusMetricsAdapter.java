package com.madlanga.blastradius.telemetry.provider.prometheus;

import com.madlanga.blastradius.telemetry.config.TelemetryConfig.PrometheusProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.madlanga.blastradius.telemetry.model.CoverageStatus;
import com.madlanga.blastradius.telemetry.model.EvidenceFamily;
import com.madlanga.blastradius.telemetry.model.EvidenceProvenance;
import com.madlanga.blastradius.telemetry.model.MetricEvidence;
import com.madlanga.blastradius.telemetry.model.TelemetryQuery;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;

/**
 * Adapter that queries Prometheus {@code /api/v1/query_range} for each configured
 * metric selector and maps the response to normalized {@link MetricEvidence}.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Build a PromQL expression from provider-neutral query context.</li>
 *   <li>Call the Prometheus HTTP API.</li>
 *   <li>Parse Prometheus matrix/instant response.</li>
 *   <li>Sanitize all label values through {@link TelemetrySanitizer}.</li>
 *   <li>Map to {@link MetricEvidence} — no Prometheus types escape this class.</li>
 *   <li>Report coverage and warnings honestly, including partial metric retrieval.</li>
 * </ol>
 *
 * <p>A Prometheus {@code up} value of 1 only means the scrape succeeded. It does NOT
 * mean the service or its dependencies are healthy. This adapter does not interpret
 * metric values as health states.</p>
 */
@Component
public class PrometheusMetricsAdapter {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMetricsAdapter.class);

    private final PrometheusProperties properties;
    private final TelemetrySanitizer sanitizer;
    private final RestClient restClient;

    public PrometheusMetricsAdapter(PrometheusProperties properties,
                                    TelemetrySanitizer sanitizer,
                                    RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.sanitizer = sanitizer;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public MetricAdapterResult fetchMetrics(TelemetryQuery query) {
        List<MetricEvidence> allMetrics = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (String selector : properties.getMetricSelectors()) {
            try {
                List<MetricEvidence> series = fetchSeries(selector, query);
                allMetrics.addAll(series);
                successCount++;
            } catch (RestClientException e) {
                failureCount++;
                log.warn("Prometheus adapter: failed to fetch '{}' [{}]",
                        selector, e.getClass().getSimpleName());
                warnings.add("Prometheus: metric '" + selector + "' unavailable — HTTP client error");
            } catch (Exception e) {
                failureCount++;
                log.warn("Prometheus adapter: unexpected error fetching '{}' [{}]",
                        selector, e.getClass().getSimpleName());
                warnings.add("Prometheus: unexpected error for metric '" + selector + "'");
            }
        }

        CoverageStatus coverage;
        if (successCount == 0) {
            coverage = CoverageStatus.UNAVAILABLE;
            if (warnings.isEmpty()) {
                warnings.add("Prometheus: no metrics could be retrieved for the requested window.");
            }
        } else if (failureCount > 0) {
            coverage = CoverageStatus.PARTIAL;
        } else if (allMetrics.isEmpty()) {
            coverage = CoverageStatus.UNAVAILABLE;
            warnings.add("Prometheus: queries succeeded but returned no data points for the window.");
        } else {
            coverage = CoverageStatus.AVAILABLE;
        }

        return new MetricAdapterResult(allMetrics, coverage, warnings);
    }

    // -------------------------------------------------------------------------
    // PromQL query and HTTP call
    // -------------------------------------------------------------------------

    private List<MetricEvidence> fetchSeries(String metricName, TelemetryQuery query) {
        String promql = buildPromQL(metricName, query);
        String sourceRef = metricName;

        PrometheusResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query_range")
                        .queryParam("query", promql)
                        .queryParam("start", query.getFrom().getEpochSecond())
                        .queryParam("end", query.getTo().getEpochSecond())
                        .queryParam("step", "5s")
                        .build())
                .retrieve()
                .body(PrometheusResponse.class);

        if (response == null || response.data == null || response.data.result == null) {
            return List.of();
        }

        return mapSeriesToEvidence(response.data.result, metricName,
                query.getEnvironment(), sourceRef);
    }

    /**
     * Build a PromQL selector. If a component filter is given, restrict by the
     * {@code service} label that Micrometer exports for our mock services.
     */
    private String buildPromQL(String metricName, TelemetryQuery query) {
        if (query.hasComponentFilter()) {
            String services = String.join("|", query.getComponentFilter());
            return metricName + "{service=~\"" + services + "\"}";
        }
        return metricName;
    }

    // -------------------------------------------------------------------------
    // Mapping: Prometheus series → MetricEvidence
    // -------------------------------------------------------------------------

    private List<MetricEvidence> mapSeriesToEvidence(List<PrometheusResponse.Series> seriesList,
                                                      String metricName,
                                                      String environment,
                                                      String sourceRef) {
        List<MetricEvidence> result = new ArrayList<>();
        Instant collectedAt = Instant.now();

        for (PrometheusResponse.Series series : seriesList) {
            // Sanitize label dimensions (metric labels are generally safe,
            // but run through sanitizer defensively)
            Map<String, String> rawLabels = new LinkedHashMap<>(series.metric);
            // Remove internal Prometheus name label — it's already the metric name
            rawLabels.remove("__name__");
            Map<String, String> sanitizedLabels = sanitizer.sanitizeAttributes(rawLabels);

            String service = sanitizedLabels.getOrDefault("service",
                    sanitizedLabels.getOrDefault("job", "unknown"));

            String unit = deriveUnit(metricName);
            String normalizedName = normalizeMetricName(metricName);

            // Use query_range values if present, otherwise fall back to instant value
            List<List<Object>> dataPoints = series.values != null && !series.values.isEmpty()
                    ? series.values
                    : wrapInstantValue(series.value);

            EvidenceProvenance provenance = EvidenceProvenance.of(
                    EvidenceFamily.METRICS,
                    properties.getProviderId(),
                    collectedAt,
                    sourceRef);

            for (List<Object> point : dataPoints) {
                if (point.size() < 2) continue;

                Instant timestamp = parsePrometheusTimestamp(point.get(0));
                double value = parsePrometheusValue(point.get(1));

                // Build a dimension map without the service label (already promoted)
                Map<String, String> dims = new LinkedHashMap<>(sanitizedLabels);
                dims.remove("service");
                dims.remove("environment");

                MetricEvidence evidence = MetricEvidence.builder()
                        .id(com.madlanga.blastradius.telemetry.model.EvidenceIdentity.of(properties.getProviderId(), service, timestamp, normalizedName, new java.util.TreeMap<>(dims)))
                        .timestamp(timestamp)
                        .service(service)
                        .environment(environment)
                        .name(normalizedName)
                        .value(value)
                        .unit(unit)
                        .dimensions(dims)
                        .provenance(provenance)
                        .build();

                result.add(evidence);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private static Instant parsePrometheusTimestamp(Object raw) {
        try {
            double epochSeconds = Double.parseDouble(raw.toString());
            long seconds = (long) epochSeconds;
            int nanos = (int) ((epochSeconds - seconds) * 1_000_000_000);
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private static double parsePrometheusValue(Object raw) {
        try {
            return Double.parseDouble(raw.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static List<List<Object>> wrapInstantValue(List<Object> value) {
        if (value == null || value.isEmpty()) return List.of();
        return List.of(value);
    }

    /**
     * Derive a normalized unit string from the metric name convention.
     * Micrometer uses {@code _seconds} and {@code _bytes} suffixes.
     */
    private static String deriveUnit(String metricName) {
        if (metricName.endsWith("_seconds_count") || metricName.endsWith("_total")) {
            return "requests";
        }
        if (metricName.endsWith("_seconds_sum") || metricName.endsWith("_seconds_max")
                || metricName.endsWith("_seconds")) {
            return "seconds";
        }
        if (metricName.endsWith("_bytes")) {
            return "bytes";
        }
        if (metricName.endsWith("_usage") || metricName.endsWith("_ratio")) {
            return "ratio";
        }
        return "";
    }

    /**
     * Convert Prometheus/Micrometer snake_case metric names to dot notation
     * for the provider-neutral domain model.
     * E.g. {@code http_server_requests_seconds_count} → {@code http.server.requests.seconds.count}
     */
    private static String normalizeMetricName(String name) {
        return name.replace('_', '.');
    }

    // -------------------------------------------------------------------------
    // Result container
    // -------------------------------------------------------------------------

    public static final class MetricAdapterResult {
        private final List<MetricEvidence> metrics;
        private final CoverageStatus coverage;
        private final List<String> warnings;

        public MetricAdapterResult(List<MetricEvidence> metrics,
                            CoverageStatus coverage,
                            List<String> warnings) {
            this.metrics = metrics;
            this.coverage = coverage;
            this.warnings = warnings;
        }

        /** Factory: UNAVAILABLE result with a single explanatory warning. */
        public static MetricAdapterResult unavailable(String warning) {
            return new MetricAdapterResult(List.of(), CoverageStatus.UNAVAILABLE, List.of(warning));
        }

        public List<MetricEvidence> getMetrics() { return metrics; }
        public CoverageStatus getCoverage() { return coverage; }
        public List<String> getWarnings() { return warnings; }
    }

    // Prometheus response DTO -------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PrometheusResponse {
    
        @JsonProperty("status")
        String status;
    
        @JsonProperty("data")
        Data data;
    
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Data {
            @JsonProperty("resultType")
            String resultType;
    
            @JsonProperty("result")
            List<Series> result = Collections.emptyList();
        }
    
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Series {
            /**
             * Label set for this time series.
             * E.g. {"service":"document-service","status":"503","uri":"/api/documents",
             *        "__name__":"http_server_requests_seconds_count"}
             */
            @JsonProperty("metric")
            Map<String, String> metric = Collections.emptyMap();
    
            /**
             * For {@code query_range}: array of [unix-timestamp-float, value-string] pairs.
             * For {@code query} (instant): single [unix-timestamp-float, value-string] pair
             * returned as {@code value} — handled separately.
             */
            @JsonProperty("values")
            List<List<Object>> values = Collections.emptyList();
    
            /** Instant query result — single [timestamp, value] pair. */
            @JsonProperty("value")
            List<Object> value = Collections.emptyList();
        }
    }
    
}
