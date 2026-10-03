package com.madlanga.blastradius.api;

import com.madlanga.blastradius.domain.incident.IncidentAnalysis;
import com.madlanga.blastradius.service.IncidentAnalysisService;
import com.madlanga.blastradius.ports.DependencyTopologyProvider;
import com.madlanga.blastradius.domain.topology.DependencyTopology;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/blast-radius")
public class BlastRadiusController {
    private final IncidentAnalysisService service;
    private final DependencyTopologyProvider topologyProvider;

    public BlastRadiusController(IncidentAnalysisService service, DependencyTopologyProvider topologyProvider) {
        this.service = service;
        this.topologyProvider = topologyProvider;
    }

    @GetMapping("/topology")
    public DependencyTopology topology(@RequestParam String applicationId,
            @RequestParam(defaultValue = "local") String environment) {
        return topologyProvider.getTopology(applicationId, environment);
    }

    /**
     * Backward-compatible query endpoint retained while clients migrate to POST.
     */
    @GetMapping("/analyze")
    public IncidentAnalysis analyze(
            @RequestParam String applicationId,
            @RequestParam(defaultValue = "local") String environment,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String originHint,
            @RequestParam(required = false) String experimentId) {
        return executeAnalysis(applicationId, environment, from, to, originHint, experimentId);
    }

    /**
     * Phase 8 analysis contract. The request body is transport-only; deterministic
     * analysis remains unchanged in IncidentAnalysisService.
     */
    @PostMapping("/analyze")
    public IncidentAnalysis analyze(@RequestBody AnalyzeIncidentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        return executeAnalysis(request.applicationId(), request.environment(), request.from(), request.to(),
                request.originHint(), request.experimentId());
    }

    private IncidentAnalysis executeAnalysis(String applicationId, String environment, Instant from, Instant to,
            String originHint, String experimentId) {
        String resolvedApplicationId = requireText(applicationId, "applicationId");
        String resolvedEnvironment = hasText(environment) ? environment.trim() : "local";
        Instant resolvedTo = to == null ? Instant.now() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(15, ChronoUnit.MINUTES) : from;
        if (!resolvedFrom.isBefore(resolvedTo)) {
            throw new ApiRequestException("INVALID_TIME_WINDOW", "from must be before to");
        }
        return service.analyze(resolvedApplicationId, resolvedEnvironment, resolvedFrom, resolvedTo,
                trimToNull(originHint), trimToNull(experimentId));
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
        if (message.startsWith("unknown failure experiment:")) return "UNKNOWN_EXPERIMENT";
        if (message.startsWith("originHint is not present in discovered topology:")) return "ORIGIN_NOT_IN_TOPOLOGY";
        if (message.startsWith("originHint conflicts with controlled experiment origin:")) return "ORIGIN_CONFLICT";
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

    record ErrorResponse(String code, String message) {}

    private static final class ApiRequestException extends IllegalArgumentException {
        private final String code;

        private ApiRequestException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() { return code; }
    }
}
