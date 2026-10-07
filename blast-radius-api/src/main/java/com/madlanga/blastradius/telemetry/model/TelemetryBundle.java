package com.madlanga.blastradius.telemetry.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The complete normalized telemetry collection for one incident/analysis window.
 *
 * <p>All four evidence families are present as typed lists; the {@link TelemetryCoverage}
 * record reports which families were available, partial, unavailable or not supported.
 * Warnings explain gaps, partial data, provider errors, or other data-quality concerns.</p>
 *
 * <p>Key invariants enforced here:
 * <ul>
 *   <li>An empty list in any family does NOT imply health — check {@code coverage}.</li>
 *   <li>UNAVAILABLE coverage for a family is an explicit reported state, never silently
 *       fabricated as empty-equals-healthy.</li>
 *   <li>All evidence in this bundle has been sanitized by the time it reaches this object.
 *       Raw provider payloads must never populate these fields directly.</li>
 * </ul>
 */
public final class TelemetryBundle {

    private final List<LogEvidence> logs;
    private final List<MetricEvidence> metrics;
    private final List<SpanEvidence> spans;
    private final List<HealthEvidence> health;
    private final TelemetryCoverage coverage;
    /** Human-readable warnings about data quality, provider failures, partial evidence. */
    private final List<String> warnings;

    private TelemetryBundle(Builder builder) {
        this.logs = Collections.unmodifiableList(new ArrayList<>(builder.logs));
        this.metrics = Collections.unmodifiableList(new ArrayList<>(builder.metrics));
        this.spans = Collections.unmodifiableList(new ArrayList<>(builder.spans));
        this.health = Collections.unmodifiableList(new ArrayList<>(builder.health));
        this.coverage = Objects.requireNonNull(builder.coverage, "coverage must not be null");
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
    }

    public List<LogEvidence> getLogs() { return logs; }
    public List<MetricEvidence> getMetrics() { return metrics; }
    public List<SpanEvidence> getSpans() { return spans; }
    public List<HealthEvidence> getHealth() { return health; }
    public TelemetryCoverage getCoverage() { return coverage; }
    public List<String> getWarnings() { return warnings; }

    public boolean hasWarnings() { return !warnings.isEmpty(); }
    public boolean hasLogs() { return !logs.isEmpty(); }
    public boolean hasMetrics() { return !metrics.isEmpty(); }
    public boolean hasSpans() { return !spans.isEmpty(); }
    public boolean hasHealthEvidence() { return !health.isEmpty(); }

    /**
     * True only when all four families are AVAILABLE.
     * Partial coverage is not full coverage.
     */
    public boolean isFullyCovered() { return coverage.isFullyCovered(); }

    public static Builder builder() { return new Builder(); }

    /** Convenience: empty bundle with all families UNAVAILABLE and a single warning. */
    public static TelemetryBundle unavailable(String reason) {
        return builder()
                .coverage(TelemetryCoverage.allUnavailable())
                .warning(reason)
                .build();
    }

    public static final class Builder {
        private List<LogEvidence> logs = new ArrayList<>();
        private List<MetricEvidence> metrics = new ArrayList<>();
        private List<SpanEvidence> spans = new ArrayList<>();
        private List<HealthEvidence> health = new ArrayList<>();
        private TelemetryCoverage coverage = TelemetryCoverage.allUnavailable();
        private List<String> warnings = new ArrayList<>();

        private Builder() {}

        public Builder logs(List<LogEvidence> logs) {
            this.logs = logs != null ? logs : new ArrayList<>();
            return this;
        }
        public Builder metrics(List<MetricEvidence> metrics) {
            this.metrics = metrics != null ? metrics : new ArrayList<>();
            return this;
        }
        public Builder spans(List<SpanEvidence> spans) {
            this.spans = spans != null ? spans : new ArrayList<>();
            return this;
        }
        public Builder health(List<HealthEvidence> health) {
            this.health = health != null ? health : new ArrayList<>();
            return this;
        }
        public Builder coverage(TelemetryCoverage coverage) {
            this.coverage = coverage;
            return this;
        }
        public Builder warning(String warning) {
            if (warning != null && !warning.isBlank()) {
                this.warnings.add(warning);
            }
            return this;
        }
        public Builder warnings(List<String> warnings) {
            this.warnings = warnings != null ? warnings : new ArrayList<>();
            return this;
        }

        public TelemetryBundle build() { return new TelemetryBundle(this); }
    }

    @Override
    public String toString() {
        return "TelemetryBundle{logs=" + logs.size()
                + ", metrics=" + metrics.size()
                + ", spans=" + spans.size()
                + ", health=" + health.size()
                + ", coverage=" + coverage
                + ", warnings=" + warnings.size() + '}';
    }
}
