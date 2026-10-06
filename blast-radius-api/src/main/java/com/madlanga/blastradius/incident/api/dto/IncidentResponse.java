package com.madlanga.blastradius.incident.api.dto;

import java.time.Instant;
import java.util.UUID;
import com.madlanga.blastradius.incident.domain.IncidentStatus;
import tools.jackson.databind.JsonNode;

public record IncidentResponse(
        UUID id,
        String applicationId,
        String environment,
        IncidentStatus status,
        Instant startedAt,
        Instant resolvedAt,
        String originComponent,
        com.madlanga.blastradius.incident.domain.ConfidenceLevel originConfidence,
        com.madlanga.blastradius.incident.domain.SeverityLevel severityLevel,
        int severityScore,
        Instant analysisFrom,
        Instant analysisTo,
        JsonNode analysis,
        Instant createdAt,
        Instant updatedAt) {
}
