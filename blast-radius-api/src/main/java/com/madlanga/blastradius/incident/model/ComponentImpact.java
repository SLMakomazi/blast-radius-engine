package com.madlanga.blastradius.incident.model;

import java.util.List;

public record ComponentImpact(
        String component,
        State state,
        Integer distance,
        List<String> path,
        List<EvidenceSignal> evidence) {

    public ComponentImpact {
        path = path == null ? List.of() : List.copyOf(path);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public enum State {
        ORIGIN, OBSERVED, THEORETICAL_ONLY, UNKNOWN, UNEXPECTED
    }
}
