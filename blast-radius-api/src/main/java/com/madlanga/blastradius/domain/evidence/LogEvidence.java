package com.madlanga.blastradius.domain.evidence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized log event — provider-neutral representation of a single log record.
 *
 * <p>Does NOT contain Loki-specific JSON fields, stream selectors, or any raw
 * provider payload. The adapter layer maps from Loki/Datadog/other formats to
 * this model and applies sanitization before returning.</p>
 *
 * <p>Sensitive values (Authorization headers, Bearer tokens, passwords, API keys,
 * PII) must be redacted by the sanitization layer BEFORE populating this object.
 * The {@code message} and {@code attributes} fields must arrive clean.</p>
 */
public final class LogEvidence {

    private final String id;
    private final Instant timestamp;
    /** Logical component/service name (e.g. "document-service"). */
    private final String service;
    private final String environment;
    /** Normalized severity: TRACE, DEBUG, INFO, WARN, ERROR, FATAL. */
    private final String level;
    /** Sanitized log message body. */
    private final String message;
    /** Business correlation ID when present. */
    private final String correlationId;
    /** W3C trace ID when available (links to span evidence). */
    private final String traceId;
    /** W3C span ID when available. */
    private final String spanId;
    /** Sanitized key/value structured attributes. */
    private final Map<String, String> attributes;
    private final EvidenceProvenance provenance;

    private LogEvidence(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
        this.service = Objects.requireNonNull(builder.service, "service must not be null");
        this.environment = builder.environment != null ? builder.environment : "";
        this.level = builder.level != null ? builder.level : "UNKNOWN";
        this.message = builder.message != null ? builder.message : "";
        this.correlationId = builder.correlationId;
        this.traceId = builder.traceId;
        this.spanId = builder.spanId;
        this.attributes = Collections.unmodifiableMap(
                new LinkedHashMap<>(builder.attributes));
        this.provenance = Objects.requireNonNull(builder.provenance, "provenance must not be null");
    }

    public String getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public String getService() { return service; }
    public String getEnvironment() { return environment; }
    public String getLevel() { return level; }
    public String getMessage() { return message; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public Map<String, String> getAttributes() { return attributes; }
    public EvidenceProvenance getProvenance() { return provenance; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String service;
        private String environment;
        private String level;
        private String message;
        private String correlationId;
        private String traceId;
        private String spanId;
        private Map<String, String> attributes = new LinkedHashMap<>();
        private EvidenceProvenance provenance;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder service(String service) { this.service = service; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder level(String level) { this.level = level; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder spanId(String spanId) { this.spanId = spanId; return this; }
        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes != null ? attributes : new LinkedHashMap<>();
            return this;
        }
        public Builder provenance(EvidenceProvenance provenance) { this.provenance = provenance; return this; }

        public LogEvidence build() { return new LogEvidence(this); }
    }

    @Override
    public String toString() {
        return "LogEvidence{id='" + id + "', service='" + service
                + "', level='" + level + "', timestamp=" + timestamp + '}';
    }
}
