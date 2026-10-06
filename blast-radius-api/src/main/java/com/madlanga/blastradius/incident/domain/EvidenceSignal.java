package com.madlanga.blastradius.incident.domain;
import java.time.Instant;
import java.util.Objects;
public record EvidenceSignal(Instant timestamp, String component, String family, String signal, String evidenceId) {
 public EvidenceSignal { Objects.requireNonNull(timestamp); Objects.requireNonNull(component); Objects.requireNonNull(family); Objects.requireNonNull(signal); }
}
