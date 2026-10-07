package com.madlanga.blastradius.telemetry.provider.loki;

import com.madlanga.blastradius.telemetry.config.TelemetryConfig.LokiProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.json.JsonMapper;
import com.madlanga.blastradius.telemetry.model.CoverageStatus;
import com.madlanga.blastradius.telemetry.model.EvidenceFamily;
import com.madlanga.blastradius.telemetry.model.EvidenceProvenance;
import com.madlanga.blastradius.telemetry.model.LogEvidence;
import com.madlanga.blastradius.telemetry.model.TelemetryQuery;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;

/**
 * Adapter that queries Loki's {@code /loki/api/v1/query_range} API and maps
 * the response to normalized {@link LogEvidence}.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Build a LogQL query from the provider-neutral {@link TelemetryQuery}.</li>
 *   <li>Call the Loki HTTP API using Spring RestClient.</li>
 *   <li>Parse the Loki-specific JSON response.</li>
 *   <li>Sanitize all attribute values through {@link TelemetrySanitizer}.</li>
 *   <li>Map to {@link LogEvidence} — no Loki types escape this class.</li>
 *   <li>Report {@link CoverageStatus} and warnings honestly.</li>
 * </ol>
 *
 * <p>This class is internal to the adapter layer. The domain and ports must never
 * import or depend on it directly.</p>
 */
@Component
public class LokiLogAdapter {

    private static final Logger log = LoggerFactory.getLogger(LokiLogAdapter.class);

    private final LokiProperties properties;
    private final TelemetrySanitizer sanitizer;
    private final RestClient restClient;
    private final JsonMapper objectMapper;

    public LokiLogAdapter(LokiProperties properties,
                          TelemetrySanitizer sanitizer,
                          RestClient.Builder restClientBuilder,
                          JsonMapper objectMapper) {
        this.properties = properties;
        this.sanitizer = sanitizer;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Public API consumed by the composite adapter
    // -------------------------------------------------------------------------

    /**
     * Retrieve and normalize log evidence for the given query window.
     *
     * @return result containing coverage status, normalized logs, and any warnings
     */
    public LogAdapterResult fetchLogs(TelemetryQuery query) {
        try {
            String logql = buildLogQL(query);
            String sourceRef = "{service_name=~\"...\"}";

            LokiResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/loki/api/v1/query_range")
                            .queryParam("query", "{query}")
                            .queryParam("start", "{start}")
                            .queryParam("end", "{end}")
                            .queryParam("limit", "{limit}")
                            .build(Map.of(
                                    "query", logql,
                                    "start", toNanoString(query.getFrom()),
                                    "end", toNanoString(query.getTo()),
                                    "limit", properties.getLimit())))
                    .retrieve()
                    .body(LokiResponse.class);

            if (response == null || response.data == null
                    || response.data.result == null
                    || response.data.result.isEmpty()) {
                return LogAdapterResult.unavailable(
                        "Loki returned no log streams for the query window.");
            }

            List<LogEvidence> logs = mapStreamsToEvidence(
                    response.data.result, query.getEnvironment(), sourceRef);

            CoverageStatus coverage = logs.isEmpty()
                    ? CoverageStatus.UNAVAILABLE
                    : CoverageStatus.AVAILABLE;

            List<String> warnings = new ArrayList<>();
            if (logs.isEmpty()) {
                warnings.add("Loki: no log records found for the requested window and component filter.");
            }

            return new LogAdapterResult(logs, coverage, warnings);

        } catch (RestClientException e) {
            log.warn("Loki adapter: HTTP request failed [{}]", e.getClass().getSimpleName());
            return LogAdapterResult.unavailable("Loki: provider unreachable — HTTP client error");
        } catch (Exception e) {
            log.warn("Loki adapter: unexpected error [{}]", e.getClass().getSimpleName());
            return LogAdapterResult.unavailable("Loki: unexpected error retrieving logs");
        }
    }

    // -------------------------------------------------------------------------
    // LogQL query construction
    // -------------------------------------------------------------------------

    /**
     * Build a LogQL stream selector from the provider-neutral query.
     * The Collector indexes only {@code service_name} and
     * {@code deployment_environment_name}; correlation ID and trace ID are
     * structured metadata queried as pipeline filters.
     *
     * <p>All user-supplied values are escaped via {@link #escapeLogQLStringLiteral}
     * before being interpolated into quoted LogQL strings, preventing any value
     * containing backslashes, double quotes or control characters from altering
     * the intended query structure.</p>
     */
    private String buildLogQL(TelemetryQuery query) {
        StringBuilder sb = new StringBuilder();

        if (query.hasComponentFilter()) {
            // Join component names with | for a regex selector.
            // Each component name is escaped so it cannot break the surrounding "..." string.
            String services = query.getComponentFilter().stream()
                    .map(LokiLogAdapter::escapeLogQLStringLiteral)
                    .reduce((a, b) -> a + "|" + b)
                    .orElse("");
            sb.append("{service_name=~\"").append(services).append("\"")
              .append(",deployment_environment_name=\"")
              .append(escapeLogQLStringLiteral(query.getEnvironment()))
              .append("\"}");
        } else {
            sb.append("{deployment_environment_name=\"")
              .append(escapeLogQLStringLiteral(query.getEnvironment()))
              .append("\"}");
        }

        // Pipeline filters for correlation/trace IDs
        if (query.hasCorrelationId()) {
            sb.append(" | correlation_id=\"")
              .append(escapeLogQLStringLiteral(query.getCorrelationId()))
              .append("\"");
        }
        if (query.hasTraceId()) {
            sb.append(" | trace_id=\"")
              .append(escapeLogQLStringLiteral(query.getTraceId()))
              .append("\"");
        }

        return sb.toString();
    }

    /**
     * Escape a value for use inside a double-quoted LogQL string literal.
     *
     * <p>Characters that must be escaped to prevent breaking the surrounding
     * {@code "..."} delimiter or producing unexpected query behaviour:
     * <ul>
     *   <li>{@code \} backslash — must be doubled to {@code \\}</li>
     *   <li>{@code "} double-quote — escaped to {@code \"}</li>
     *   <li>newline {@code \n} — escaped to {@code \n}</li>
     *   <li>carriage return {@code \r} — escaped to {@code \r}</li>
     *   <li>tab {@code \t} — escaped to {@code \t}</li>
     * </ul>
     *
     * <p>This method is Loki-specific and must not be reused for other
     * query languages.</p>
     *
     * @param value raw input value; must not be null
     * @return escaped value safe for embedding in a LogQL double-quoted literal
     */
    static String escapeLogQLStringLiteral(String value) {
        // Process backslash first — must be done before inserting any new backslashes
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n",  "\\n")
                .replace("\r",  "\\r")
                .replace("\t",  "\\t");
    }

    // -------------------------------------------------------------------------
    // Mapping: Loki streams → LogEvidence
    // -------------------------------------------------------------------------

    private List<LogEvidence> mapStreamsToEvidence(List<LokiResponse.Stream> streams,
                                                    String environment,
                                                    String sourceRef) {
        List<LogEvidence> results = new ArrayList<>();
        Instant collectedAt = Instant.now();

        for (LokiResponse.Stream stream : streams) {
            String serviceName = stream.stream.getOrDefault("service_name", "unknown");
            String streamCorrelationId = stream.stream.get("correlation_id");
            String streamTraceId = stream.stream.get("trace_id");
            String streamSpanId = stream.stream.get("span_id");
            String streamSeverityText = stream.stream.get("severity_text");

            for (List<String> value : stream.values) {
                if (value.size() < 2) continue;

                String timestampNanos = value.get(0);
                String rawLine = value.get(1);

                Instant timestamp = parseNanoTimestamp(timestampNanos);
                LokiLogLine logLine = parseLogLine(rawLine);

                EvidenceProvenance provenance = EvidenceProvenance.of(
                        EvidenceFamily.LOGS,
                        properties.getProviderId(),
                        collectedAt,
                        sourceRef);

                // Build sanitized attributes from extra fields
                Map<String, String> rawAttrs = new LinkedHashMap<>(logLine.getExtraAttributes());
                Map<String, String> sanitizedAttrs = sanitizer.sanitizeAttributes(rawAttrs);

                String sanitizedMessage = sanitizer.sanitizeMessage(logLine.body);

                LogEvidence evidence = LogEvidence.builder()
                        .id("log-" + UUID.randomUUID())
                        .timestamp(timestamp)
                        .service(serviceName)
                        .environment(environment)
                        .level(normalizeLevel(firstNonBlank(streamSeverityText, logLine.severityText)))
                        .message(sanitizedMessage)
                        .correlationId(firstNonBlank(streamCorrelationId, logLine.correlationId))
                        .traceId(firstNonBlank(streamTraceId, logLine.traceId))
                        .spanId(firstNonBlank(streamSpanId, logLine.spanId))
                        .attributes(sanitizedAttrs)
                        .provenance(provenance)
                        .build();

                results.add(evidence);
            }
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private LokiLogLine parseLogLine(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return new LokiLogLine();
        }
        // Log lines may be plain text or JSON; try JSON first
        if (rawLine.startsWith("{")) {
            try {
                return objectMapper.readValue(rawLine, LokiLogLine.class);
            } catch (Exception e) {
                // Fall through: treat as plain text message
            }
        }
        LokiLogLine plain = new LokiLogLine();
        plain.body = rawLine;
        return plain;
    }

    /** Convert a Loki nanosecond Unix timestamp string to {@link Instant}. */
    private static Instant parseNanoTimestamp(String nanos) {
        try {
            long ns = Long.parseLong(nanos);
            long seconds = ns / 1_000_000_000L;
            int remainingNanos = (int) (ns % 1_000_000_000L);
            return Instant.ofEpochSecond(seconds, remainingNanos);
        } catch (NumberFormatException e) {
            return Instant.now();
        }
    }

    /** Convert an {@link Instant} to a nanosecond Unix timestamp string for Loki. */
    private static String toNanoString(Instant instant) {
        return String.valueOf(instant.getEpochSecond() * 1_000_000_000L + instant.getNano());
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    /** Normalize severity text to a consistent uppercase level string. */
    private static String normalizeLevel(String severityText) {
        if (severityText == null || severityText.isBlank()) return "UNKNOWN";
        return switch (severityText.toUpperCase()) {
            case "TRACE", "TRACE2", "TRACE3", "TRACE4" -> "TRACE";
            case "DEBUG", "DEBUG2", "DEBUG3", "DEBUG4" -> "DEBUG";
            case "INFO",  "INFO2",  "INFO3",  "INFO4"  -> "INFO";
            case "WARN",  "WARN2",  "WARN3",  "WARN4"  -> "WARN";
            case "ERROR", "ERROR2", "ERROR3", "ERROR4" -> "ERROR";
            case "FATAL", "FATAL2", "FATAL3", "FATAL4" -> "FATAL";
            default -> severityText.toUpperCase();
        };
    }

    // -------------------------------------------------------------------------
    // Result container
    // -------------------------------------------------------------------------

    /** Carries the result of a single Loki fetch, including coverage and warnings. */
    public static final class LogAdapterResult {
        private final List<LogEvidence> logs;
        private final CoverageStatus coverage;
        private final List<String> warnings;

        public LogAdapterResult(List<LogEvidence> logs, CoverageStatus coverage, List<String> warnings) {
            this.logs = logs;
            this.coverage = coverage;
            this.warnings = warnings;
        }

        /** Factory: UNAVAILABLE result with a single explanatory warning. */
        public static LogAdapterResult unavailable(String warning) {
            return new LogAdapterResult(List.of(), CoverageStatus.UNAVAILABLE, List.of(warning));
        }

        public List<LogEvidence> getLogs() { return logs; }
        public CoverageStatus getCoverage() { return coverage; }
        public List<String> getWarnings() { return warnings; }
    }

    // Loki response DTOs ------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LokiResponse {
    
        @JsonProperty("status")
        String status;
    
        @JsonProperty("data")
        Data data;
    
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Data {
            @JsonProperty("resultType")
            String resultType;
    
            @JsonProperty("result")
            List<Stream> result = Collections.emptyList();
        }
    
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Stream {
            /** Loki stream labels, e.g. {"service_name":"document-service","deployment_environment_name":"local"}. */
            @JsonProperty("stream")
            Map<String, String> stream = Collections.emptyMap();
    
            /**
             * Array of [nanosecond-unix-timestamp-string, log-line-string] pairs.
             */
            @JsonProperty("values")
            List<List<String>> values = Collections.emptyList();
        }
    }
    

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LokiLogLine {
    
        /** Severity/level as emitted by the agent, e.g. "INFO", "ERROR", "WARN". */
        @JsonProperty("severityText")
        String severityText;
    
        /** The log message body. */
        @JsonProperty("body")
        String body;
    
        /** W3C trace ID if the log was emitted within a trace context. */
        @JsonProperty("traceId")
        String traceId;
    
        /** W3C span ID if the log was emitted within a span context. */
        @JsonProperty("spanId")
        String spanId;
    
        /** Business correlation ID (renamed from correlationId by the Collector transform). */
        @JsonProperty("correlation_id")
        String correlationId;
    
        /**
         * Catch-all for any remaining structured fields not explicitly mapped above.
         * These are treated as additional attributes and sanitized before use.
         */
        private final Map<String, String> extraAttributes = new LinkedHashMap<>();
    
        @JsonAnySetter
        void setExtraAttribute(String key, Object value) {
            if (value != null) {
                extraAttributes.put(key, value.toString());
            }
        }
    
        Map<String, String> getExtraAttributes() {
            return extraAttributes;
        }
    }
    
}
