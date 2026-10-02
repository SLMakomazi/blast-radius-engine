package com.madlanga.blastradius.adapters.telemetry.tempo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Internal DTO for the Tempo {@code /api/search} HTTP response.
 *
 * <p>INTERNAL TO THIS PACKAGE.
 *
 * <p>Used to discover trace IDs within a time window before fetching full traces.
 * Shape:
 * <pre>
 * {
 *   "traces": [
 *     {
 *       "traceID": "abc123...",
 *       "rootServiceName": "payment-service",
 *       "rootTraceName": "POST /api/payments",
 *       "startTimeUnixNano": "...",
 *       "durationMs": 1250
 *     },
 *     ...
 *   ]
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class TempoSearchResponse {

    @JsonProperty("traces")
    List<TraceSummary> traces = Collections.emptyList();

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TraceSummary {
        @JsonProperty("traceID")
        String traceId;

        @JsonProperty("rootServiceName")
        String rootServiceName;

        @JsonProperty("rootTraceName")
        String rootTraceName;

        @JsonProperty("startTimeUnixNano")
        String startTimeUnixNano;

        @JsonProperty("durationMs")
        long durationMs;
    }
}
