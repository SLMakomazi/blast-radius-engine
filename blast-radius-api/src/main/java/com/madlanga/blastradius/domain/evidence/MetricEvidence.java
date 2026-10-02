package com.madlanga.blastradius.domain.evidence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized metric sample — provider-neutral representation of a single time-series
 * data point.
 *
 * <p>Capable of representing traffic/request volume, latency, error rate/count,
 * HTTP status behaviour, zero-traffic, JVM/process info, downstream request metrics
 * and database pool metrics — without being coupled to Prometheus label syntax or
 * PromQL conventions.</p>
 *
 * <p>The adapter layer maps from provider-specific formats (Prometheus instant query
 * result, Datadog metric series, etc.) and sanitizes before returning this object.</p>
 */
public final class MetricEvidence {

    private final String id;
    private final Instant timestamp;
    /** Logical component/service name (e.g. "document-service"). */
    private final String service;
    private final String environment;
    /**
     * Normalized metric name using dot notation.
     * Examples: "http.server.requests.count", "http.server.requests.latency.sum",
     * "hikaricp.connections.active", "jvm.memory.used".
     */
    private final String name;
    private final double value;
    /** Optional unit, e.g. "seconds", "bytes", "ratio", "requests". */
    private final String unit;
    /** Sanitized dimensions/labels, e.g. {"status":"503", "uri":"/api/documents"}. */
    private final Map<String, String> dimensions;
    private final EvidenceProvenance provenance;

    private MetricEvidence(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
        this.service = Objects.requireNonNull(builder.service, "service must not be null");
        this.environment = builder.environment != null ? builder.environment : "";
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.value = builder.value;
        this.unit = builder.unit != null ? builder.unit : "";
        this.dimensions = Collections.unmodifiableMap(
                new LinkedHashMap<>(builder.dimensions));
        this.provenance = Objects.requireNonNull(builder.provenance, "provenance must not be null");
    }

    public String getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public String getService() { return service; }
    public String getEnvironment() { return environment; }
    public String getName() { return name; }
    public double getValue() { return value; }
    public String getUnit() { return unit; }
    public Map<String, String> getDimensions() { return dimensions; }
    public EvidenceProvenance getProvenance() { return provenance; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String service;
        private String environment;
        private String name;
        private double value;
        private String unit;
        private Map<String, String> dimensions = new LinkedHashMap<>();
        private EvidenceProvenance provenance;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder service(String service) { this.service = service; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder value(double value) { this.value = value; return this; }
        public Builder unit(String unit) { this.unit = unit; return this; }
        public Builder dimensions(Map<String, String> dimensions) {
            this.dimensions = dimensions != null ? dimensions : new LinkedHashMap<>();
            return this;
        }
        public Builder provenance(EvidenceProvenance provenance) { this.provenance = provenance; return this; }

        public MetricEvidence build() { return new MetricEvidence(this); }
    }

    @Override
    public String toString() {
        return "MetricEvidence{id='" + id + "', service='" + service
                + "', name='" + name + "', value=" + value + ", timestamp=" + timestamp + '}';
    }
}
