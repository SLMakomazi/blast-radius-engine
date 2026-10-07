package com.madlanga.blastradius.incident.controller;

import com.madlanga.blastradius.incident.service.IncidentAnalysisService;
import com.madlanga.blastradius.incident.model.IncidentAnalysis;
import com.madlanga.blastradius.lifecycle.service.IncidentLifecycleService;
import com.madlanga.blastradius.topology.application.port.DependencyTopologyProvider;
import com.madlanga.blastradius.topology.domain.DependencyTopology;
import java.time.Instant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.madlanga.blastradius.incident.dto.AnalyzeIncidentRequest;
import com.madlanga.blastradius.incident.dto.ErrorResponse;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/blast-radius")
public class IncidentController {
    private final IncidentAnalysisService analysisService;
    private final DependencyTopologyProvider topologyProvider;
    private final IncidentLifecycleService lifecycleService;

    public IncidentController(IncidentAnalysisService analysisService, DependencyTopologyProvider topologyProvider,
            IncidentLifecycleService lifecycleService) {
        this.analysisService = analysisService;
        this.topologyProvider = topologyProvider;
        this.lifecycleService = lifecycleService;
    }

    @Operation(summary = "Get dependency topology", description = "Returns the deterministic dependency topology used to calculate potential blast radius.")
    @GetMapping("/topology")
    public DependencyTopology topology(@RequestParam String applicationId,
            @RequestParam(defaultValue = "local") String environment) {
        return topologyProvider.getTopology(applicationId, environment);
    }

    /**
     * Backward-compatible query endpoint retained while clients migrate to POST.
     */
    @Operation(summary = "Analyze blast radius using query parameters", description = "Backward-compatible analysis endpoint retained while clients migrate to POST.")
    @GetMapping("/analyze")
    public IncidentAnalysis analyze(
            @RequestParam String applicationId,
            @RequestParam(defaultValue = "local") String environment,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String originHint) {
        return executeAnalysis(applicationId, environment, from, to, originHint, false);
    }

    /**
     * Request-body analysis contract. The request DTO remains transport-only;
     * deterministic analysis stays in IncidentAnalysisService.
     */
    @Operation(summary = "Analyze incident blast radius", description = "Correlates topology and available telemetry to calculate deterministic theoretical and observed impact. AI does not determine blast radius.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analysis completed"),
            @ApiResponse(responseCode = "400", description = "Invalid request or analysis cannot be completed for the requested evidence/window", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/analyze")
    public IncidentAnalysis analyze(@RequestBody AnalyzeIncidentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        return executeAnalysis(request.applicationId(), request.environment(), request.from(), request.to(),
                request.originHint(), true);
    }

    private IncidentAnalysis executeAnalysis(String applicationId, String environment, Instant from, Instant to,
            String originHint, boolean persistLifecycle) {
        String resolvedApplicationId = requireText(applicationId, "applicationId");
        String resolvedEnvironment = hasText(environment) ? environment.trim() : "local";
        Instant resolvedTo = to == null ? Instant.now() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(15, ChronoUnit.MINUTES) : from;
        if (!resolvedFrom.isBefore(resolvedTo)) {
            throw new ApiRequestException("INVALID_TIME_WINDOW", "from must be before to");
        }
        String resolvedOriginHint = trimToNull(originHint);
        if (persistLifecycle) {
            return lifecycleService.analyzeAndPersist(resolvedApplicationId, resolvedEnvironment, resolvedFrom, resolvedTo,
                    resolvedOriginHint);
        }
        return analysisService.analyze(resolvedApplicationId, resolvedEnvironment, resolvedFrom, resolvedTo,
                resolvedOriginHint);
    }

    @ExceptionHandler(ApiRequestException.class)
    ResponseEntity<ErrorResponse> requestError(ApiRequestException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.code(), e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidAnalysisRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(classifyIllegalArgument(e), e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> analysisUnavailable(IllegalStateException e) {
        String code = e.getMessage() != null && e.getMessage().startsWith("No failure evidence found")
                ? "NO_FAILURE_EVIDENCE"
                : "ANALYSIS_NOT_AVAILABLE";
        return ResponseEntity.badRequest().body(new ErrorResponse(code, e.getMessage()));
    }

    private String classifyIllegalArgument(IllegalArgumentException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.startsWith("originHint is not present in discovered topology:")) return "ORIGIN_NOT_IN_TOPOLOGY";
        return "INVALID_REQUEST";
    }

    private String requireText(String value, String field) {
        if (!hasText(value)) {
            throw new ApiRequestException("INVALID_REQUEST", field + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class ApiRequestException extends IllegalArgumentException {
        private final String code;

        private ApiRequestException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() { return code; }
    }
}
