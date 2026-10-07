package com.madlanga.blastradius.incident.dto;

import java.time.Instant;
import java.util.UUID;
import com.madlanga.blastradius.incident.model.ConfidenceLevel;
import com.madlanga.blastradius.incident.model.IncidentStatus;
import com.madlanga.blastradius.incident.model.SeverityLevel;
import tools.jackson.databind.JsonNode;

public record IncidentResponse(
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
        JsonNode analysis,
        Instant createdAt,
        Instant updatedAt) {
}
