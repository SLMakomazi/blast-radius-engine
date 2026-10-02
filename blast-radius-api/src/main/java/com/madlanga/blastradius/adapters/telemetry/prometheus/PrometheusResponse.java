package com.madlanga.blastradius.adapters.telemetry.prometheus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Internal DTO for the Prometheus {@code /api/v1/query_range} HTTP response.
 *
 * <p>INTERNAL TO THIS PACKAGE. Never referenced outside the prometheus adapter.
 *
 * <p>Response shape (relevant subset):
 * <pre>
 * {
 *   "status": "success",
 *   "data": {
 *     "resultType": "matrix",
 *     "result": [
 *       {
 *         "metric": { "service": "document-service", "status": "503", "uri": "/api/documents" },
 *         "values": [
 *           [1234567890.123, "14"],
 *           ...
 *         ]
 *       }
 *     ]
 *   }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class PrometheusResponse {

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
