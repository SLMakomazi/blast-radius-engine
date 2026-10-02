package com.madlanga.blastradius.domain.evidence;

import java.time.Instant;
import java.util.Objects;

/**
 * Provenance metadata attached to every normalized evidence item.
 *
 * <p>Answers:
 * <ul>
 *   <li>Which evidence family produced this?</li>
 *   <li>Which provider produced it (e.g. "local-loki", "local-prometheus",
 *       "local-tempo", "local-actuator")?</li>
 *   <li>When was it collected?</li>
 *   <li>A short opaque reference to the raw backend source (stream label, metric
 *       name, trace ID prefix, endpoint path) — NOT the full raw payload.</li>
 * </ul>
 *
 * <p>Raw provider responses are never stored here; only enough information for
 * auditability and debugging.</p>
 */
public final class EvidenceProvenance {

    private final EvidenceFamily family;
    private final String provider;
    private final Instant collectedAt;
    /** Short opaque reference, e.g. a Loki stream selector or Prometheus metric name. */
    private final String sourceRef;

    private EvidenceProvenance(EvidenceFamily family, String provider,
                               Instant collectedAt, String sourceRef) {
        this.family = Objects.requireNonNull(family, "family must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.collectedAt = Objects.requireNonNull(collectedAt, "collectedAt must not be null");
        this.sourceRef = sourceRef != null ? sourceRef : "";
    }

    public static EvidenceProvenance of(EvidenceFamily family, String provider,
                                        Instant collectedAt, String sourceRef) {
        return new EvidenceProvenance(family, provider, collectedAt, sourceRef);
    }

    public EvidenceFamily getFamily() {
        return family;
    }

    public String getProvider() {
        return provider;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EvidenceProvenance that)) return false;
        return family == that.family
                && provider.equals(that.provider)
                && collectedAt.equals(that.collectedAt)
                && sourceRef.equals(that.sourceRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(family, provider, collectedAt, sourceRef);
    }

    @Override
    public String toString() {
        return "EvidenceProvenance{family=" + family
                + ", provider='" + provider + '\''
                + ", collectedAt=" + collectedAt
                + ", sourceRef='" + sourceRef + '\''
                + '}';
    }
}
