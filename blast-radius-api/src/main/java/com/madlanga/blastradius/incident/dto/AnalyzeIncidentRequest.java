package com.madlanga.blastradius.incident.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * HTTP request data for incident analysis.
 * IncidentAnalysisService performs the analysis.
 */
@Schema(description = "Request a deterministic blast-radius analysis for an application and time window.")
public record AnalyzeIncidentRequest(
        @Schema(description = "Application identifier.", example = "document-platform", requiredMode = Schema.RequiredMode.REQUIRED)
        String applicationId,
        @Schema(description = "Deployment environment. Defaults to local when omitted or blank.", example = "local")
        String environment,
        @Schema(description = "Inclusive analysis-window start. Defaults to 15 minutes before 'to'.", example = "2026-10-03T05:20:12Z")
        Instant from,
        @Schema(description = "Analysis-window end. Defaults to the current time.", example = "2026-10-03T05:20:54Z")
        Instant to,
        @Schema(description = "Optional suspected failure origin for theoretical analysis.", example = "postgres")
        String originHint) {
}
