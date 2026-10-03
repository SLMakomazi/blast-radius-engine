package com.madlanga.blastradius.domain.incident;

import java.util.List;

public record IncidentSeverity(SeverityLevel level, int score, List<String> reasons) {
    public IncidentSeverity {
        if (score < 0 || score > 100) throw new IllegalArgumentException("score must be between 0 and 100");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
