package com.madlanga.blastradius.telemetry.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized distributed trace span — provider-neutral representation of a single span.
 *
 * <p>Capable of representing cross-service propagation:
 * payment-service → customer-service → document-service → PostgreSQL.</p>
 *
 * <p>Does NOT contain Tempo-specific JSON structures, OTLP proto field names, or
 * Datadog trace formats. The adapter layer maps from provider payloads and sanitizes
 * before populating this object.</p>
 *
 * <p>Exception messages and stack traces are intentionally excluded (they are
 * stripped at the Collector level per Phase 3 privacy configuration). Only the
 * error type and status are retained.</p>
 */
public final class SpanEvidence {

    private final String id;
    private final String traceId;
    private final String spanId;
    /** Null or empty means this is a root span. */
    private final String parentSpanId;
    /** Logical component/service name. */
    private final String service;
    private final String environment;
    /** Span/operation name, e.g. "POST /api/documents", "INSERT synthetic_lab.synthetic_documents". */
    private final String operation;
    private final Instant startTime;
    private final long durationMs;
    private final SpanStatus status;
    private final SpanKind kind;
    /**
     * Peer service when this span represents a client-side call to another component.
     * E.g. "postgres" for a JDBC span emitted by document-service.
     */
    private final String peerService;
    /** Sanitized span attributes. */
    private final Map<String, String> attributes;
    /** Error type/classification if status is ERROR; null otherwise. */
    private final String errorType;
    private final EvidenceProvenance provenance;

    private SpanEvidence(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.traceId = Objects.requireNonNull(builder.traceId, "traceId must not be null");
        this.spanId = Objects.requireNonNull(builder.spanId, "spanId must not be null");
        this.parentSpanId = builder.parentSpanId;
        this.service = Objects.requireNonNull(builder.service, "service must not be null");
        this.environment = builder.environment != null ? builder.environment : "";
        this.operation = builder.operation != null ? builder.operation : "";
        this.startTime = Objects.requireNonNull(builder.startTime, "startTime must not be null");
        this.durationMs = builder.durationMs;
        this.kind = builder.kind;
        this.status = builder.status != null ? builder.status : SpanStatus.UNSET;
        this.peerService = builder.peerService;
        this.attributes = Collections.unmodifiableMap(
                new LinkedHashMap<>(builder.attributes));
        this.errorType = builder.errorType;
        this.provenance = Objects.requireNonNull(builder.provenance, "provenance must not be null");
    }

    public String getId() { return id; }
    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public String getService() { return service; }
    public String getEnvironment() { return environment; }
    public String getOperation() { return operation; }
    public Instant getStartTime() { return startTime; }
    public long getDurationMs() { return durationMs; }
    public SpanKind getKind() { return kind; }
    public SpanStatus getStatus() { return status; }
    public String getPeerService() { return peerService; }
    public Map<String, String> getAttributes() { return attributes; }
    public String getErrorType() { return errorType; }
    public EvidenceProvenance getProvenance() { return provenance; }

    public boolean isError() { return SpanStatus.ERROR == status; }
    public boolean isRootSpan() { return parentSpanId == null || parentSpanId.isBlank(); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String traceId;
        private String spanId;
        private String parentSpanId;
        private String service;
        private String environment;
        private String operation;
        private Instant startTime;
        private long durationMs;
        private SpanStatus status;
        private SpanKind kind = SpanKind.UNKNOWN;
        private String peerService;
        private Map<String, String> attributes = new LinkedHashMap<>();
        private String errorType;
        private EvidenceProvenance provenance;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder spanId(String spanId) { this.spanId = spanId; return this; }
        public Builder parentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; return this; }
        public Builder service(String service) { this.service = service; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder operation(String operation) { this.operation = operation; return this; }
        public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder kind(SpanKind kind) { this.kind = kind == null ? SpanKind.UNKNOWN : kind; return this; }
        public Builder status(SpanStatus status) { this.status = status; return this; }
        public Builder peerService(String peerService) { this.peerService = peerService; return this; }
        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes != null ? attributes : new LinkedHashMap<>();
            return this;
        }
        public Builder errorType(String errorType) { this.errorType = errorType; return this; }
        public Builder provenance(EvidenceProvenance provenance) { this.provenance = provenance; return this; }

        public SpanEvidence build() { return new SpanEvidence(this); }
    }

    @Override
    public String toString() {
        return "SpanEvidence{id='" + id + "', service='" + service
                + "', traceId='" + traceId + "', operation='" + operation
                + "', status=" + status + ", durationMs=" + durationMs + '}';
    }
}
