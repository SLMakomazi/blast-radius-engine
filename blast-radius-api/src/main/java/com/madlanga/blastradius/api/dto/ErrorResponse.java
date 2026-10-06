package com.madlanga.blastradius.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stable API error contract.")
public record ErrorResponse(
        @Schema(description = "Machine-readable error code.", example = "INVALID_REQUEST") String code,
        @Schema(description = "Human-readable error detail.", example = "applicationId is required") String message) {}

