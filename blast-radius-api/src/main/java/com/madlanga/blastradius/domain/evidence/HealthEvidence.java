package com.madlanga.blastradius.domain.evidence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Normalized health observation — provider-neutral representation of a health check result.
 *
 * <p>Capable of distinguishing UP / DOWN / DEGRADED / UNKNOWN states without
 * being coupled to Spring Boot Actuator JSON shapes, Datadog monitor states,
 * or any other vendor model.</p>
 *
 * <p>CRITICAL: A UNKNOWN or DOWN state here represents a real observed health
 * signal. A missing HealthEvidence (CoverageStatus.UNAVAILABLE) means the
 * health check could not be performed — it does NOT mean the component is healthy.</p>
 */
public final class HealthEvidence {

    private final String id;
    private final Instant timestamp;
    /** Logical component/service name. */
    private final String service;
    private final String environment;
    /**
     * Specific health endpoint or check identity.
     * E.g. "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness",
     * or a named check like "db", "diskSpace".
     */
    private final String endpoint;
    private final HealthState state;
    /** HTTP status code when the check was performed via HTTP; null if not applicable. */
    private final Integer httpStatus;
    /**
     * Round-trip latency to the health endpoint in milliseconds.
     * Null if not measured or not applicable.
     */
    private final Long latencyMs;
    /** Sanitized additional details (e.g. {"db":"UP", "diskSpace":"UP"}). */
    private final Map<String, String> details;
    private final EvidenceProvenance provenance;

    private HealthEvidence(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
        this.service = Objects.requireNonNull(builder.service, "service must not be null");
        this.environment = builder.environment != null ? builder.environment : "";
        this.endpoint = builder.endpoint != null ? builder.endpoint : "";
        this.state = Objects.requireNonNull(builder.state, "state must not be null");
        this.httpStatus = builder.httpStatus;
        this.latencyMs = builder.latencyMs;
        this.details = Collections.unmodifiableMap(
                new LinkedHashMap<>(builder.details));
        this.provenance = Objects.requireNonNull(builder.provenance, "provenance must not be null");
    }

    public String getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public String getService() { return service; }
    public String getEnvironment() { return environment; }
    public String getEndpoint() { return endpoint; }
    public HealthState getState() { return state; }
    public Integer getHttpStatus() { return httpStatus; }
    public Long getLatencyMs() { return latencyMs; }
    public Map<String, String> getDetails() { return details; }
    public EvidenceProvenance getProvenance() { return provenance; }

    public boolean isHealthy() { return HealthState.UP == state; }
    public boolean isDegraded() { return HealthState.DEGRADED == state || HealthState.DOWN == state; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String service;
        private String environment;
        private String endpoint;
        private HealthState state;
        private Integer httpStatus;
        private Long latencyMs;
        private Map<String, String> details = new LinkedHashMap<>();
        private EvidenceProvenance provenance;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder service(String service) { this.service = service; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder endpoint(String endpoint) { this.endpoint = endpoint; return this; }
        public Builder state(HealthState state) { this.state = state; return this; }
        public Builder httpStatus(Integer httpStatus) { this.httpStatus = httpStatus; return this; }
        public Builder latencyMs(Long latencyMs) { this.latencyMs = latencyMs; return this; }
        public Builder details(Map<String, String> details) {
            this.details = details != null ? details : new LinkedHashMap<>();
            return this;
        }
        public Builder provenance(EvidenceProvenance provenance) { this.provenance = provenance; return this; }

        public HealthEvidence build() { return new HealthEvidence(this); }
    }

    @Override
    public String toString() {
        return "HealthEvidence{id='" + id + "', service='" + service
                + "', endpoint='" + endpoint + "', state=" + state
                + ", timestamp=" + timestamp + '}';
    }
}
