package com.madlanga.blastradius.api;

import com.madlanga.blastradius.domain.incident.IncidentStatus;
import com.madlanga.blastradius.domain.incident.PersistedIncident;
import com.madlanga.blastradius.ports.IncidentRepository;
import com.madlanga.blastradius.service.IncidentLifecycleService;
import com.madlanga.blastradius.service.AiDiagnosisService;
import com.madlanga.blastradius.domain.diagnosis.AiDiagnosis;
import com.madlanga.blastradius.domain.incident.IncidentAnalysis;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/blast-radius/incidents")
public class IncidentHistoryController {
    private final IncidentRepository repository;
    private final JsonMapper jsonMapper;
    private final IncidentLifecycleService lifecycleService;
    private final AiDiagnosisService aiDiagnosisService;

    public IncidentHistoryController(IncidentRepository repository, JsonMapper jsonMapper,
            IncidentLifecycleService lifecycleService, AiDiagnosisService aiDiagnosisService) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.lifecycleService = lifecycleService;
        this.aiDiagnosisService = aiDiagnosisService;
    }

    @Operation(summary = "List persisted blast radius incidents")
    @GetMapping
    public List<IncidentResponse> list(
            @RequestParam(required = false) String applicationId,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        String normalizedStatus = normalizeStatus(status);
        return repository.find(applicationId, environment, normalizedStatus, from, to).stream()
                .map(this::response)
                .toList();
    }

    @Operation(summary = "Get one persisted blast radius incident")
    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> get(@PathVariable UUID id) {
        return repository.findById(id)
                .map(this::response)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Generate an advisory AI diagnosis for a persisted incident",
            description = "Uses the persisted sanitized deterministic analysis as the source of truth. The AI does not calculate blast radius. If the configured AI provider fails, a deterministic fallback is returned.")
    @PostMapping("/{id}/diagnosis")
    public ResponseEntity<?> diagnose(@PathVariable UUID id) {
        var incident = repository.findById(id);
        if (incident.isEmpty()) {
            return ResponseEntity.status(404).body(new HistoryErrorResponse("INCIDENT_NOT_FOUND", "incident not found: " + id));
        }
        try {
            IncidentAnalysis analysis = jsonMapper.readValue(incident.get().analysisSnapshot(), IncidentAnalysis.class);
            AiDiagnosis diagnosis = aiDiagnosisService.diagnose(analysis);
            return ResponseEntity.ok(diagnosis);
        } catch (Exception e) {
            throw new IllegalStateException("stored incident snapshot cannot be diagnosed", e);
        }
    }

    @Operation(summary = "Explicitly resolve an active blast radius incident")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolve(@PathVariable UUID id, @RequestBody(required = false) ResolveIncidentRequest request) {
        try {
            Instant resolvedAt = request == null ? null : request.resolvedAt();
            return ResponseEntity.ok(response(lifecycleService.resolve(id, resolvedAt).orElseThrow()));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("incident not found:")) {
                return ResponseEntity.status(404).body(new HistoryErrorResponse("INCIDENT_NOT_FOUND", e.getMessage()));
            }
            throw e;
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<HistoryErrorResponse> invalidRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new HistoryErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        try {
            IncidentStatus.valueOf(normalized);
            return normalized;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("status must be ACTIVE or RESOLVED");
        }
    }

    private IncidentResponse response(PersistedIncident incident) {
        try {
            return new IncidentResponse(
                    incident.id(), incident.applicationId(), incident.environment(), incident.status(),
                    incident.startedAt(), incident.resolvedAt(), incident.originComponent(),
                    incident.originConfidence(), incident.severityLevel(), incident.severityScore(),
                    incident.analysisFrom(), incident.analysisTo(), jsonMapper.readTree(incident.analysisSnapshot()),
                    incident.createdAt(), incident.updatedAt());
        } catch (Exception e) {
            throw new IllegalStateException("stored incident snapshot is not valid JSON", e);
        }
    }

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

    public record ResolveIncidentRequest(Instant resolvedAt) {
    }

    record HistoryErrorResponse(String code, String message) {
    }
}
