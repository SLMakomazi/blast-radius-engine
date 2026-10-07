package com.madlanga.blastradius.telemetry.provider.loki;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal DTO for a single structured log line embedded within a Loki stream value.
 *
 * <p>INTERNAL TO THIS PACKAGE. The log line format is produced by the
 * OpenTelemetry Java agent via the Collector's controlled logger scope export.
 * Only the fields that survive the Collector's transform/privacy processor are
 * expected here.</p>
 *
 * <p>Any unknown fields captured by {@code @JsonAnySetter} are passed through
 * attribute sanitization before entering the domain — they are never assumed safe.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class LokiLogLine {

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
