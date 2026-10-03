package com.madlanga.blastradius.api;

import java.time.Instant;

/**
 * Phase 8 transport contract for incident analysis.
 * Domain analysis remains in IncidentAnalysisService.
 */
public record AnalyzeIncidentRequest(
        String applicationId,
        String environment,
        Instant from,
        Instant to,
        String originHint,
        String experimentId) {
}
