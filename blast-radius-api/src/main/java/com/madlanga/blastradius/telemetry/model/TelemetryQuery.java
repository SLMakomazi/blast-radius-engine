package com.madlanga.blastradius.telemetry.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral query describing the telemetry window and scope for a Blast Radius
 * analysis request.
 *
 * <p>This object carries no provider-specific syntax: no LogQL, no PromQL, no Tempo
 * API paths, no Datadog facets. Provider adapters translate this into their own
 * query syntax internally.</p>
 *
 * <p>All fields are optional except {@code applicationId}, {@code environment},
 * {@code from} and {@code to}.</p>
 */
public final class TelemetryQuery {

    private final String applicationId;
    private final String environment;
    private final Instant from;
    private final Instant to;
    /**
     * Optional list of component/service names to focus retrieval.
     * Empty means "all components in the application".
     */
    private final List<String> componentFilter;
    /** Optional business correlation ID to scope log/trace retrieval. */
    private final String correlationId;
    /** Optional W3C trace ID to scope span/log retrieval. */
    private final String traceId;

    private TelemetryQuery(Builder builder) {
        this.applicationId = Objects.requireNonNull(builder.applicationId, "applicationId must not be null");
        this.environment = Objects.requireNonNull(builder.environment, "environment must not be null");
        this.from = Objects.requireNonNull(builder.from, "from must not be null");
        this.to = Objects.requireNonNull(builder.to, "to must not be null");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        this.componentFilter = Collections.unmodifiableList(
                new ArrayList<>(builder.componentFilter));
        this.correlationId = builder.correlationId;
        this.traceId = builder.traceId;
    }

    public String getApplicationId() { return applicationId; }
    public String getEnvironment() { return environment; }
    public Instant getFrom() { return from; }
    public Instant getTo() { return to; }
    public List<String> getComponentFilter() { return componentFilter; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceId() { return traceId; }

    public boolean hasComponentFilter() { return !componentFilter.isEmpty(); }
    public boolean hasCorrelationId() { return correlationId != null && !correlationId.isBlank(); }
    public boolean hasTraceId() { return traceId != null && !traceId.isBlank(); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String applicationId;
        private String environment;
        private Instant from;
        private Instant to;
        private List<String> componentFilter = new ArrayList<>();
        private String correlationId;
        private String traceId;

        private Builder() {}

        public Builder applicationId(String applicationId) { this.applicationId = applicationId; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder from(Instant from) { this.from = from; return this; }
        public Builder to(Instant to) { this.to = to; return this; }
        public Builder componentFilter(List<String> componentFilter) {
            this.componentFilter = componentFilter != null ? componentFilter : new ArrayList<>();
            return this;
        }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }

        public TelemetryQuery build() { return new TelemetryQuery(this); }
    }

    @Override
    public String toString() {
        return "TelemetryQuery{applicationId='" + applicationId
                + "', environment='" + environment
                + "', from=" + from + ", to=" + to + '}';
    }
}
