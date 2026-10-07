package com.madlanga.blastradius.incident.model;

import java.time.Instant;
import java.util.UUID;

public record PersistedIncident(
        UUID id,
        String applicationId,
        String environment,
        Status status,
        Instant startedAt,
        Instant resolvedAt,
        String originComponent,
        OriginAssessment.Confidence originConfidence,
        IncidentSeverity.Level severityLevel,
        int severityScore,
        Instant analysisFrom,
        Instant analysisTo,
        String analysisSnapshot,
        Instant createdAt,
        Instant updatedAt) {

    public enum Status {
        ACTIVE, RESOLVED
    }
}
