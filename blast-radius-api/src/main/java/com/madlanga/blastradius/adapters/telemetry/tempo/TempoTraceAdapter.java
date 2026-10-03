package com.madlanga.blastradius.adapters.telemetry.tempo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.madlanga.blastradius.domain.evidence.CoverageStatus;
import com.madlanga.blastradius.domain.evidence.EvidenceFamily;
import com.madlanga.blastradius.domain.evidence.EvidenceProvenance;
import com.madlanga.blastradius.domain.evidence.SpanEvidence;
import com.madlanga.blastradius.domain.evidence.SpanStatus;
import com.madlanga.blastradius.domain.evidence.SpanKind;
import com.madlanga.blastradius.domain.evidence.TelemetryQuery;
import com.madlanga.blastradius.sanitization.TelemetrySanitizer;

/**
 * Adapter that queries Tempo for distributed trace data and maps the response to
 * normalized {@link SpanEvidence}.
 *
 * <p>Strategy:
 * <ol>
 *   <li>If the query specifies a trace ID, fetch that trace directly via
 *       {@code GET /api/traces/{traceId}}.</li>
 *   <li>Otherwise, use {@code GET /api/search} with the time window to discover
 *       relevant trace IDs, then fetch each trace.</li>
 * </ol>
 *
 * <p>The Collector's {@code transform/privacy} processor has already removed
 * exception messages, stack traces, SQL text and authorization values from span
 * attributes before they reach Tempo. This adapter sanitizes defensively anyway.</p>
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Build Tempo API calls from provider-neutral context.</li>
 *   <li>Parse OTLP-JSON response.</li>
 *   <li>Sanitize all span attributes.</li>
 *   <li>Map to {@link SpanEvidence} — no Tempo types escape this class.</li>
 *   <li>Report coverage and warnings honestly.</li>
 * </ol>
 */
@Component
public class TempoTraceAdapter {

    private static final Logger log = LoggerFactory.getLogger(TempoTraceAdapter.class);
    private static final int TRACE_FETCH_ATTEMPTS = 3;
    private static final long TRACE_FETCH_RETRY_DELAY_MS = 200L;

    private final TempoProperties properties;
    private final TelemetrySanitizer sanitizer;
    private final RestClient restClient;

    public TempoTraceAdapter(TempoProperties properties,
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

    public TraceAdapterResult fetchSpans(TelemetryQuery query) {
        try {
            TraceAdapterResult result = query.hasTraceId()
                    ? fetchByTraceId(query.getTraceId(), query.getEnvironment()) : fetchByTimeWindow(query);
            List<SpanEvidence> inWindow = result.getSpans().stream()
                    .filter(span -> !span.getStartTime().isBefore(query.getFrom())
                            && span.getStartTime().isBefore(query.getTo())).toList();
            if (!result.getSpans().isEmpty() && inWindow.isEmpty()) {
                List<String> warnings = new ArrayList<>(result.getWarnings());
                warnings.add("Tempo: fetched traces contain no spans starting in the requested window");
                return new TraceAdapterResult(inWindow, CoverageStatus.UNAVAILABLE, warnings);
            }
            return new TraceAdapterResult(inWindow, result.getCoverage(), result.getWarnings());
        } catch (RestClientException e) {
            log.warn("Tempo adapter: HTTP request failed [{}]", e.getClass().getSimpleName());
            return TraceAdapterResult.unavailable("Tempo: provider unreachable — HTTP client error");
        } catch (Exception e) {
            log.warn("Tempo adapter: unexpected error [{}]", e.getClass().getSimpleName());
            return TraceAdapterResult.unavailable("Tempo: unexpected error retrieving spans");
        }
    }

    // -------------------------------------------------------------------------
    // Fetch strategies
    // -------------------------------------------------------------------------

    private TraceAdapterResult fetchByTraceId(String traceId, String environment) {
        TempoResponse response = restClient.get()
                .uri("/api/traces/{traceId}", traceId)
                .retrieve()
                .body(TempoResponse.class);

        if (response == null || response.batches == null || response.batches.isEmpty()) {
            return TraceAdapterResult.unavailable(
                    "Tempo: trace not found for ID (may be outside retention window).");
        }

        List<SpanEvidence> spans = mapBatchesToSpans(response.batches, environment,
                "trace/" + traceId);
        CoverageStatus coverage = spans.isEmpty()
                ? CoverageStatus.UNAVAILABLE : CoverageStatus.AVAILABLE;
        return new TraceAdapterResult(spans, coverage, List.of());
    }

    private TraceAdapterResult fetchByTimeWindow(TelemetryQuery query) {
        TempoSearchResponse searchResponse = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/search")
                        .queryParam("start", query.getFrom().getEpochSecond())
                        .queryParam("end", query.getTo().getEpochSecond())
                        .queryParam("limit", properties.getSearchLimit())
                        .build())
                .retrieve()
                .body(TempoSearchResponse.class);

        if (searchResponse == null || searchResponse.traces == null
                || searchResponse.traces.isEmpty()) {
            return TraceAdapterResult.unavailable(
                    "Tempo: no traces found for the requested time window.");
        }

        List<SpanEvidence> allSpans = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Prioritize failures within this bounded sample. Sorting cannot recover traces
        // omitted by the backend and must not be treated as complete topology discovery.
        List<TempoSearchResponse.TraceSummary> summaries = new ArrayList<>(searchResponse.traces);
        summaries.sort(java.util.Comparator
                .comparing((TempoSearchResponse.TraceSummary s) -> !s.isFailed())
                .thenComparing(s -> s.startTimeUnixNano == null ? "" : s.startTimeUnixNano,
                        java.util.Comparator.reverseOrder()));

        for (TempoSearchResponse.TraceSummary summary : summaries) {
            if (summary.traceId == null || summary.traceId.isBlank()) continue;
            try {
                TempoResponse trace = fetchTraceWithRetry(summary.traceId);

                if (trace != null && trace.batches != null) {
                    allSpans.addAll(mapBatchesToSpans(
                            trace.batches, query.getEnvironment(),
                            "trace/" + summary.traceId));
                }
            } catch (Exception e) {
                String cause = safeFailureDetail(e);
                log.warn("Tempo trace fetch failed: traceId={}, exception={}, cause={}",
                        summary.traceId, e.getClass().getSimpleName(), cause);
                warnings.add("Tempo: could not fetch trace " + summary.traceId
                        + " [" + e.getClass().getSimpleName() + "]");
            }
        }

        CoverageStatus coverage;
        if (allSpans.isEmpty() && warnings.isEmpty()) {
            coverage = CoverageStatus.UNAVAILABLE;
            warnings.add("Tempo: trace search returned results but span fetch yielded no data.");
        } else if (!warnings.isEmpty() && !allSpans.isEmpty()) {
            coverage = CoverageStatus.PARTIAL;
        } else if (allSpans.isEmpty()) {
            coverage = CoverageStatus.UNAVAILABLE;
        } else {
            coverage = CoverageStatus.AVAILABLE;
        }

        return new TraceAdapterResult(allSpans, coverage, warnings);
    }

    private static String safeFailureDetail(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }

        // Keep diagnostics bounded and single-line. Provider payloads are deliberately
        // not logged here because telemetry may contain sensitive application data.
        String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() > 300) {
            singleLine = singleLine.substring(0, 300) + "...";
        }
        return root.getClass().getSimpleName() + ": " + singleLine;
    }

    private TempoResponse fetchTraceWithRetry(String traceId) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= TRACE_FETCH_ATTEMPTS; attempt++) {
            try {
                return restClient.get()
                        .uri("/api/traces/{traceId}", traceId)
                        .retrieve()
                        .body(TempoResponse.class);
            } catch (RestClientException e) {
                lastFailure = e;
                if (attempt == TRACE_FETCH_ATTEMPTS) break;
                try {
                    Thread.sleep(TRACE_FETCH_RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw lastFailure;
    }

    // -------------------------------------------------------------------------
    // Mapping: OTLP batches → SpanEvidence
    // -------------------------------------------------------------------------

    private List<SpanEvidence> mapBatchesToSpans(List<TempoResponse.Batch> batches,
                                                  String environment,
                                                  String sourceRef) {
        List<SpanEvidence> result = new ArrayList<>();
        Instant collectedAt = Instant.now();

        for (TempoResponse.Batch batch : batches) {
            String serviceName = extractResourceAttribute(batch.resource, "service.name");

            for (TempoResponse.ScopeSpans scopeSpans : batch.scopeSpans) {
                for (TempoResponse.Span span : scopeSpans.spans) {
                    SpanEvidence evidence = mapSpan(span, serviceName, environment,
                            sourceRef, collectedAt);
                    if (evidence != null) {
                        result.add(evidence);
                    }
                }
            }
        }
        return result;
    }

    private SpanEvidence mapSpan(TempoResponse.Span span,
                                  String serviceName,
                                  String environment,
                                  String sourceRef,
                                  Instant collectedAt) {
        if (span.traceId == null || span.spanId == null) return null;

        Instant startTime = parseNanoTimestamp(span.startTimeUnixNano);
        long durationMs = computeDurationMs(span.startTimeUnixNano, span.endTimeUnixNano);

        // Collect span attributes into a sanitizable map
        Map<String, String> rawAttrs = new LinkedHashMap<>();
        if (span.attributes != null) {
            for (TempoResponse.KeyValue kv : span.attributes) {
                if (kv.key != null && kv.value != null) {
                    rawAttrs.put(kv.key, kv.value.asString());
                }
            }
        }
        Map<String, String> sanitizedAttrs = sanitizer.sanitizeAttributes(rawAttrs);

        SpanStatus status = mapSpanStatus(span.status);
        String errorType = null;
        if (status == SpanStatus.ERROR) {
            errorType = sanitizedAttrs.get("error.type");
        }

        // Derive the dependency peer without turning generic server-side
        // "server.address" values (for example localhost on an Actuator/server span)
        // into topology nodes. Technology alone never identifies a concrete dependency.
        String dbSystem = sanitizedAttrs.getOrDefault("db.system.name",
                sanitizedAttrs.get("db.system"));
        String serverAddress = sanitizedAttrs.get("server.address");
        String peerService = null;
        if (dbSystem != null) {
            // For DB spans the endpoint address identifies the concrete dependency
            // instance/service (for example "postgres"), while db.system identifies
            // its technology (for example "postgresql"). Keep those concepts separate.
            peerService = hasUsablePeerAddress(serverAddress) ? serverAddress.trim() : null;
        } else if (isClientSpan(span.kind) && hasUsablePeerAddress(serverAddress)) {
            peerService = serverAddress;
        }

        EvidenceProvenance provenance = EvidenceProvenance.of(
                EvidenceFamily.TRACES,
                properties.getProviderId(),
                collectedAt,
                sourceRef);

        return SpanEvidence.builder()
                .id("span-" + UUID.randomUUID())
                .traceId(normalizeOtlpId(span.traceId))
                .spanId(normalizeOtlpId(span.spanId))
                .parentSpanId(normalizeOtlpId(span.parentSpanId))
                .service(serviceName)
                .environment(environment)
                .operation(span.name)
                .startTime(startTime)
                .durationMs(durationMs)
                .status(status)
                .kind(mapSpanKind(span.kind))
                .peerService(peerService)
                .attributes(sanitizedAttrs)
                .errorType(errorType)
                .provenance(provenance)
                .build();
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Tempo's OTLP/JSON trace endpoint serializes byte-valued trace/span IDs as
     * Base64, while Loki and Tempo search APIs expose the same IDs as hexadecimal.
     * Normalize OTLP IDs to lowercase hex so evidence can correlate across providers.
     * If a value is already a valid hex ID, preserve it in normalized lowercase form.
     */
    static String normalizeOtlpId(String id) {
        if (id == null || id.isBlank()) return id;

        String trimmed = id.trim();
        if (trimmed.matches("(?i)^[0-9a-f]+$") && (trimmed.length() == 16 || trimmed.length() == 32)) {
            return trimmed.toLowerCase(java.util.Locale.ROOT);
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            if (decoded.length == 8 || decoded.length == 16) {
                return HexFormat.of().formatHex(decoded);
            }
        } catch (IllegalArgumentException ignored) {
            // Preserve unknown provider representations rather than inventing an ID.
        }
        return trimmed;
    }

    private static String extractResourceAttribute(TempoResponse.Resource resource, String key) {
        if (resource == null || resource.attributes == null) return "unknown";
        for (TempoResponse.KeyValue kv : resource.attributes) {
            if (key.equals(kv.key) && kv.value != null) {
                return kv.value.asString();
            }
        }
        return "unknown";
    }

    private static boolean hasUsablePeerAddress(String address) {
        if (address == null || address.isBlank()) return false;
        String normalized = address.trim().toLowerCase(java.util.Locale.ROOT);
        return !"localhost".equals(normalized)
                && !"127.0.0.1".equals(normalized)
                && !"::1".equals(normalized);
    }

    private static SpanKind mapSpanKind(String kind) {
        if (kind == null) return SpanKind.UNKNOWN;
        return switch (kind.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "1", "SPAN_KIND_INTERNAL" -> SpanKind.INTERNAL;
            case "2", "SPAN_KIND_SERVER" -> SpanKind.SERVER;
            case "3", "SPAN_KIND_CLIENT" -> SpanKind.CLIENT;
            case "4", "SPAN_KIND_PRODUCER" -> SpanKind.PRODUCER;
            case "5", "SPAN_KIND_CONSUMER" -> SpanKind.CONSUMER;
            default -> SpanKind.UNKNOWN;
        };
    }

    private static boolean isClientSpan(String kind) {
        if (kind == null) return false;
        String normalized = kind.trim().toUpperCase(java.util.Locale.ROOT);
        return "SPAN_KIND_CLIENT".equals(normalized) || "3".equals(normalized);
    }

    private static SpanStatus mapSpanStatus(TempoResponse.SpanStatusDto statusDto) {
        if (statusDto == null || statusDto.code == null) return SpanStatus.UNSET;
        return switch (statusDto.code.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "1", "STATUS_CODE_OK", "OK" -> SpanStatus.OK;
            case "2", "STATUS_CODE_ERROR", "ERROR" -> SpanStatus.ERROR;
            default -> SpanStatus.UNSET;
        };
    }

    private static Instant parseNanoTimestamp(String nanos) {
        if (nanos == null || nanos.isBlank()) return Instant.now();
        try {
            long ns = Long.parseLong(nanos);
            long seconds = ns / 1_000_000_000L;
            int remainingNanos = (int) (ns % 1_000_000_000L);
            return Instant.ofEpochSecond(seconds, remainingNanos);
        } catch (NumberFormatException e) {
            return Instant.now();
        }
    }

    private static long computeDurationMs(String startNanos, String endNanos) {
        if (startNanos == null || endNanos == null) return 0;
        try {
            long start = Long.parseLong(startNanos);
            long end = Long.parseLong(endNanos);
            return (end - start) / 1_000_000L;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Result container
    // -------------------------------------------------------------------------

    public static final class TraceAdapterResult {
        private final List<SpanEvidence> spans;
        private final CoverageStatus coverage;
        private final List<String> warnings;

        public TraceAdapterResult(List<SpanEvidence> spans, CoverageStatus coverage, List<String> warnings) {
            this.spans = spans;
            this.coverage = coverage;
            this.warnings = warnings;
        }

        /** Factory: UNAVAILABLE result with a single explanatory warning. */
        public static TraceAdapterResult unavailable(String warning) {
            return new TraceAdapterResult(List.of(), CoverageStatus.UNAVAILABLE, List.of(warning));
        }

        public List<SpanEvidence> getSpans() { return spans; }
        public CoverageStatus getCoverage() { return coverage; }
        public List<String> getWarnings() { return warnings; }
    }
}
