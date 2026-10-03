package com.madlanga.blastradius.domain.incident;

import java.time.Instant;
import java.util.UUID;

public record PersistedIncident(
        UUID id,
        String applicationId,
        String environment,
        IncidentStatus status,
        Instant startedAt,
        Instant resolvedAt,
        String originComponent,
        ConfidenceLevel originConfidence,
        SeverityLevel severityLevel,
        int severityScore,
        Instant analysisFrom,
        Instant analysisTo,
        String analysisSnapshot,
        Instant createdAt,
        Instant updatedAt) {
}
