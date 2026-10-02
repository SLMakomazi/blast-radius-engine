package com.madlanga.blastradius.domain.evidence;

import java.util.Objects;

/**
 * Coverage status for all four evidence families in a single TelemetryBundle.
 *
 * <p>INVARIANT: UNAVAILABLE or NOT_SUPPORTED for a family does NOT mean the
 * corresponding components are healthy. Telemetry availability and application
 * health are independent concepts (ADR-017).</p>
 */
public final class TelemetryCoverage {

    private final CoverageStatus logs;
    private final CoverageStatus metrics;
    private final CoverageStatus traces;
    private final CoverageStatus health;

    private TelemetryCoverage(Builder builder) {
        this.logs = Objects.requireNonNull(builder.logs, "logs coverage must not be null");
        this.metrics = Objects.requireNonNull(builder.metrics, "metrics coverage must not be null");
        this.traces = Objects.requireNonNull(builder.traces, "traces coverage must not be null");
        this.health = Objects.requireNonNull(builder.health, "health coverage must not be null");
    }

    public CoverageStatus getLogs() { return logs; }
    public CoverageStatus getMetrics() { return metrics; }
    public CoverageStatus getTraces() { return traces; }
    public CoverageStatus getHealth() { return health; }

    public boolean isFullyCovered() {
        return logs == CoverageStatus.AVAILABLE
                && metrics == CoverageStatus.AVAILABLE
                && traces == CoverageStatus.AVAILABLE
                && health == CoverageStatus.AVAILABLE;
    }

    public boolean hasAnyEvidence() {
        return logs == CoverageStatus.AVAILABLE || logs == CoverageStatus.PARTIAL
                || metrics == CoverageStatus.AVAILABLE || metrics == CoverageStatus.PARTIAL
                || traces == CoverageStatus.AVAILABLE || traces == CoverageStatus.PARTIAL
                || health == CoverageStatus.AVAILABLE || health == CoverageStatus.PARTIAL;
    }

    public static Builder builder() { return new Builder(); }

    /** Convenience factory: all four families AVAILABLE. */
    public static TelemetryCoverage allAvailable() {
        return builder()
                .logs(CoverageStatus.AVAILABLE)
                .metrics(CoverageStatus.AVAILABLE)
                .traces(CoverageStatus.AVAILABLE)
                .health(CoverageStatus.AVAILABLE)
                .build();
    }

    /** Convenience factory: all four families UNAVAILABLE. */
    public static TelemetryCoverage allUnavailable() {
        return builder()
                .logs(CoverageStatus.UNAVAILABLE)
                .metrics(CoverageStatus.UNAVAILABLE)
                .traces(CoverageStatus.UNAVAILABLE)
                .health(CoverageStatus.UNAVAILABLE)
                .build();
    }

    public static final class Builder {
        private CoverageStatus logs = CoverageStatus.UNAVAILABLE;
        private CoverageStatus metrics = CoverageStatus.UNAVAILABLE;
        private CoverageStatus traces = CoverageStatus.UNAVAILABLE;
        private CoverageStatus health = CoverageStatus.UNAVAILABLE;

        private Builder() {}

        public Builder logs(CoverageStatus logs) { this.logs = logs; return this; }
        public Builder metrics(CoverageStatus metrics) { this.metrics = metrics; return this; }
        public Builder traces(CoverageStatus traces) { this.traces = traces; return this; }
        public Builder health(CoverageStatus health) { this.health = health; return this; }

        public TelemetryCoverage build() { return new TelemetryCoverage(this); }
    }

    @Override
    public String toString() {
        return "TelemetryCoverage{logs=" + logs + ", metrics=" + metrics
                + ", traces=" + traces + ", health=" + health + '}';
    }
}
