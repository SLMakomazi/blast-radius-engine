package com.madlanga.blastradius.adapters.telemetry.tempo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Internal DTO for the Tempo {@code /api/traces/{traceId}} HTTP response.
 *
 * <p>INTERNAL TO THIS PACKAGE. Never referenced outside the tempo adapter.
 *
 * <p>Tempo returns OTLP-JSON format. Relevant shape:
 * <pre>
 * {
 *   "batches": [
 *     {
 *       "resource": {
 *         "attributes": [{"key":"service.name","value":{"stringValue":"payment-service"}}, ...]
 *       },
 *       "scopeSpans": [
 *         {
 *           "spans": [
 *             {
 *               "traceId": "...", "spanId": "...", "parentSpanId": "...",
 *               "name": "POST /api/payments",
 *               "startTimeUnixNano": "1234567890000000000",
 *               "endTimeUnixNano":   "1234567891250000000",
 *               "status": {"code": 2},
 *               "attributes": [{"key":"error.type","value":{"stringValue":"ConnectionException"}}, ...]
 *             }
 *           ]
 *         }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class TempoResponse {

    @JsonProperty("batches")
    List<Batch> batches = Collections.emptyList();

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Batch {
        @JsonProperty("resource")
        Resource resource;

        @JsonProperty("scopeSpans")
        List<ScopeSpans> scopeSpans = Collections.emptyList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Resource {
        @JsonProperty("attributes")
        List<KeyValue> attributes = Collections.emptyList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ScopeSpans {
        @JsonProperty("spans")
        List<Span> spans = Collections.emptyList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Span {
        @JsonProperty("traceId")
        String traceId;

        @JsonProperty("spanId")
        String spanId;

        @JsonProperty("parentSpanId")
        String parentSpanId;

        @JsonProperty("name")
        String name;

        @JsonProperty("startTimeUnixNano")
        String startTimeUnixNano;

        @JsonProperty("endTimeUnixNano")
        String endTimeUnixNano;

        @JsonProperty("status")
        SpanStatusDto status;

        @JsonProperty("attributes")
        List<KeyValue> attributes = Collections.emptyList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SpanStatusDto {
        /**
         * Tempo OTLP/JSON may serialize status codes either numerically
         * (0/1/2) or symbolically (STATUS_CODE_UNSET/OK/ERROR).
         */
        @JsonProperty("code")
        String code;

        @JsonProperty("message")
        String message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class KeyValue {
        @JsonProperty("key")
        String key;

        @JsonProperty("value")
        AnyValue value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AnyValue {
        @JsonProperty("stringValue")
        String stringValue;

        @JsonProperty("intValue")
        String intValue;

        @JsonProperty("boolValue")
        Boolean boolValue;

        String asString() {
            if (stringValue != null) return stringValue;
            if (intValue != null) return intValue;
            if (boolValue != null) return boolValue.toString();
            return "";
        }
    }
}
