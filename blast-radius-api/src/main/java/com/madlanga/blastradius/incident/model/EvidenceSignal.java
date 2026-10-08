package com.madlanga.blastradius.incident.model;

import java.time.Instant;
import java.util.Objects;
import com.madlanga.blastradius.telemetry.model.EvidenceProvenance;

/** A normalized observation. Occurrence time and collection time have separate meanings. */
public record EvidenceSignal(Instant timestamp, String component, String family, String signal, String evidenceId,
        Kind kind, String provider, String sourceRef, Instant collectedAt) {
    public enum Kind { SYMPTOM, POTENTIAL, AVAILABILITY_UNAVAILABLE, AVAILABILITY_AVAILABLE }
    public EvidenceSignal {
        Objects.requireNonNull(timestamp); Objects.requireNonNull(component);
        Objects.requireNonNull(family); Objects.requireNonNull(signal);
        kind = kind == null ? Kind.SYMPTOM : kind;
    }
    public EvidenceSignal(Instant timestamp, String component, String family, String signal, String evidenceId) {
        this(timestamp, component, family, signal, evidenceId, Kind.SYMPTOM, null, null, null);
    }
    public EvidenceSignal withSource(EvidenceProvenance source, Kind classification) {
        return new EvidenceSignal(timestamp, component, family, signal, evidenceId, classification,
                source.getProvider(), source.getSourceRef(), source.getCollectedAt());
    }
    public boolean confirmsUnavailable() { return kind == Kind.AVAILABILITY_UNAVAILABLE && family.equals("HEALTH"); }
}
