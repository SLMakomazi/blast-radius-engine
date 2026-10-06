package com.madlanga.blastradius.api.dto;

import java.time.Instant;
import java.util.UUID;
import com.madlanga.blastradius.domain.incident.IncidentStatus;
import tools.jackson.databind.JsonNode;

public record IncidentResponse(
        UUID id,
        String applicationId,
        String environment,
        IncidentStatus status,
        Instant startedAt,
        Instant resolvedAt,
        String originComponent,
        com.madlanga.blastradius.domain.incident.ConfidenceLevel originConfidence,
        com.madlanga.blastradius.domain.incident.SeverityLevel severityLevel,
        int severityScore,
        Instant analysisFrom,
        Instant analysisTo,
        JsonNode analysis,
        Instant createdAt,
        Instant updatedAt) {
}

