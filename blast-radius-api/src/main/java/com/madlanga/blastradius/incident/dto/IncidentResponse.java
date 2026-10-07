package com.madlanga.blastradius.incident.dto;

import java.time.Instant;
import java.util.UUID;
import com.madlanga.blastradius.incident.model.OriginAssessment.Confidence;
import com.madlanga.blastradius.incident.model.PersistedIncident.Status;
import com.madlanga.blastradius.incident.model.IncidentSeverity.Level;
import tools.jackson.databind.JsonNode;

public record IncidentResponse(
        UUID id,
        String applicationId,
        String environment,
        Status status,
        Instant startedAt,
        Instant resolvedAt,
        String originComponent,
        Confidence originConfidence,
        Level severityLevel,
        int severityScore,
        Instant analysisFrom,
        Instant analysisTo,
        JsonNode analysis,
        Instant createdAt,
        Instant updatedAt) {
}
