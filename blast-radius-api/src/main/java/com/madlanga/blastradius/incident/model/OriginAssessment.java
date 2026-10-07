package com.madlanga.blastradius.incident.model;

import java.util.List;

public record OriginAssessment(
        String component,
        Confidence confidence,
        int evidenceScore,
        List<EvidenceSignal> evidence) {

    public OriginAssessment {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public enum Confidence {
        LOW, MEDIUM, HIGH
    }
}
