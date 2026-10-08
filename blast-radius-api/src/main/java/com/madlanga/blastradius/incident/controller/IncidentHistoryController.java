package com.madlanga.blastradius.incident.controller;

import com.madlanga.blastradius.incident.dto.ErrorResponse;
import com.madlanga.blastradius.incident.dto.IncidentResponse;
import com.madlanga.blastradius.incident.dto.ResolveIncidentRequest;
import com.madlanga.blastradius.incident.model.PersistedIncident.Status;
import com.madlanga.blastradius.incident.model.PersistedIncident;
import com.madlanga.blastradius.incident.repository.IncidentRepository;
import com.madlanga.blastradius.lifecycle.service.IncidentLifecycleService;
import com.madlanga.blastradius.diagnosis.service.DiagnosisService;
import com.madlanga.blastradius.diagnosis.dto.DiagnosisResponse;
import com.madlanga.blastradius.diagnosis.dto.DiagnosisRequest;
import com.madlanga.blastradius.diagnosis.mapper.DiagnosisRequestMapper;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/blast-radius/incidents")
public class IncidentHistoryController {
    private final com.madlanga.blastradius.diagnosis.service.DiagnosisArchive archive;
    private final IncidentRepository repository;
    private final JsonMapper jsonMapper;
    private final IncidentLifecycleService lifecycleService;
    private final DiagnosisService diagnosisService;
    private final DiagnosisRequestMapper diagnosisRequestMapper;

    public IncidentHistoryController(IncidentRepository repository, JsonMapper jsonMapper,
            IncidentLifecycleService lifecycleService, DiagnosisService diagnosisService,
            DiagnosisRequestMapper diagnosisRequestMapper, com.madlanga.blastradius.diagnosis.service.DiagnosisArchive archive) {
        this.archive = archive;
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.lifecycleService = lifecycleService;
        this.diagnosisService = diagnosisService;
        this.diagnosisRequestMapper = diagnosisRequestMapper;
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
            return ResponseEntity.status(404).body(new ErrorResponse("INCIDENT_NOT_FOUND", "incident not found: " + id));
        }
        try {
            DiagnosisRequest context = diagnosisRequestMapper.fromStoredJson(incident.get().analysisSnapshot());
            DiagnosisResponse diagnosis = diagnosisService.diagnose(context);
            return ResponseEntity.ok(archive.save(incident.get(), diagnosis));
        } catch (Exception e) {
            throw new IllegalStateException("stored incident snapshot cannot be diagnosed", e);
        }
    }

    @GetMapping("/{id}/diagnoses")
    public java.util.List<tools.jackson.databind.JsonNode> diagnoses(@PathVariable UUID id) {
        return archive.list(id);
    }

    @Operation(summary = "Explicitly resolve an active blast radius incident")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolve(@PathVariable UUID id, @RequestBody(required = false) ResolveIncidentRequest request) {
        try {
            Instant resolvedAt = request == null ? null : request.resolvedAt();
            return ResponseEntity.ok(response(lifecycleService.resolve(id, resolvedAt).orElseThrow()));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("incident not found:")) {
                return ResponseEntity.status(404).body(new ErrorResponse("INCIDENT_NOT_FOUND", e.getMessage()));
            }
            throw e;
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        try {
            PersistedIncident.Status.valueOf(normalized);
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

}
