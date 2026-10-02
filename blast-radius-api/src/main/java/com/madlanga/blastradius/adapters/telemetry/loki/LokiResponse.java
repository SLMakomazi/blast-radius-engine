package com.madlanga.blastradius.adapters.telemetry.loki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Internal DTO for the Loki {@code /loki/api/v1/query_range} HTTP response.
 *
 * <p>INTERNAL TO THIS PACKAGE. Never referenced outside the loki adapter.
 * The domain model must not depend on this class.</p>
 *
 * <p>Response shape (relevant subset):
 * <pre>
 * {
 *   "status": "success",
 *   "data": {
 *     "resultType": "streams",
 *     "result": [
 *       {
 *         "stream": { "service_name": "document-service", ... },
 *         "values": [
 *           ["1234567890000000000", "{...json log line...}"],
 *           ...
 *         ]
 *       }
 *     ]
 *   }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class LokiResponse {

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
