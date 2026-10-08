package com.madlanga.blastradius.lifecycle.service;

import com.madlanga.blastradius.incident.model.IncidentSeverity;
import com.madlanga.blastradius.incident.model.EvidenceSignal;
import com.madlanga.blastradius.incident.model.IncidentAnalysis;
import com.madlanga.blastradius.incident.model.PersistedIncident;
import com.madlanga.blastradius.incident.repository.IncidentRepository;
import com.madlanga.blastradius.incident.service.IncidentAnalysisService;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class IncidentLifecycleService {
    private final IncidentAnalysisService analysisService;
    private final IncidentRepository repository;
    private final JsonMapper jsonMapper;
    @org.springframework.beans.factory.annotation.Value("${blast-radius.lifecycle.max-evidence-per-incident:20000}")
    private int maxEvidence = 20000;
    @org.springframework.beans.factory.annotation.Value("${blast-radius.lifecycle.collection-duration:7d}")
    private java.time.Duration collectionDuration = java.time.Duration.ofDays(7);


    public IncidentLifecycleService(IncidentAnalysisService analysisService, IncidentRepository repository, JsonMapper jsonMapper) {
        this.analysisService = analysisService;
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @org.springframework.transaction.annotation.Transactional
    public IncidentAnalysis analyzeAndPersist(String applicationId, String environment, Instant from, Instant to,
            String originHint) {
        IncidentAnalysis analysis = analysisService.analyze(applicationId, environment, from, to, originHint);
        persistLifecycle(analysis);
        return analysis;
    }

    @org.springframework.transaction.annotation.Transactional
    public Optional<PersistedIncident> persistLifecycle(IncidentAnalysis analysis) {
        if (analysis.origin() == null || analysis.severity() == null) return Optional.empty();

        repository.lockScope(analysis.applicationId(), analysis.environment(), analysis.origin().component());
        boolean failureObserved = hasFailureEvidence(analysis);
        Optional<PersistedIncident> active = repository.findActive(
                analysis.applicationId(), analysis.environment(), analysis.origin().component());

        if (!failureObserved && active.isEmpty()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        var lastResolution = repository.latestResolution(analysis.applicationId(), analysis.environment(), analysis.origin().component());
        if (active.isEmpty() && lastResolution.isPresent() && analysis.origin().evidence().stream()
                .filter(EvidenceSignal::confirmsUnavailable).noneMatch(e -> e.timestamp().isAfter(lastResolution.get()))) return Optional.empty();
        if (active.isPresent() && !analysis.to().isAfter(active.get().analysisTo())) {
            // The observation is older than the current accepted evaluation.
            // Never replace the current availability assessment with stale data.
            return active;
        }
        String snapshot = new IncidentEvidenceAccumulator(jsonMapper).merge(active.map(PersistedIncident::analysisSnapshot).orElse(null),
                analysis, Math.max(1, maxEvidence), collectionDuration);
        var merged = jsonMapper.readTree(snapshot);


        if (active.isPresent()) {
            PersistedIncident existing = active.get();
            return Optional.of(repository.save(new PersistedIncident(
                    existing.id(), existing.applicationId(), existing.environment(), PersistedIncident.Status.ACTIVE,
                    existing.startedAt(), null, existing.originComponent(), existing.originConfidence(),
                    IncidentSeverity.Level.valueOf(merged.path("severity").path("level").asText()), merged.path("severity").path("score").asInt(),
                    existing.analysisFrom(), analysis.to().isAfter(existing.analysisTo()) ? analysis.to() : existing.analysisTo(),
                    snapshot, existing.createdAt(), now)));
        }

        Instant startedAt = analysis.origin().evidence().stream().filter(EvidenceSignal::confirmsUnavailable)
                .map(EvidenceSignal::timestamp)
                .min(Comparator.naturalOrder())
                .orElse(analysis.from());
        return Optional.of(repository.save(new PersistedIncident(
                UUID.randomUUID(), analysis.applicationId(), analysis.environment(), PersistedIncident.Status.ACTIVE,
                startedAt, null, analysis.origin().component(), analysis.origin().confidence(),
                analysis.severity().level(), analysis.severity().score(), analysis.from(), analysis.to(),
                snapshot, now, now)));
    }

    @org.springframework.transaction.annotation.Transactional
    public Optional<PersistedIncident> resolve(UUID incidentId, Instant resolvedAt) {
        PersistedIncident existing = repository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("incident not found: " + incidentId));
        repository.lockScope(existing.applicationId(), existing.environment(), existing.originComponent());
        existing = repository.findById(incidentId).orElseThrow();
        if (existing.status() == PersistedIncident.Status.RESOLVED) {
            return Optional.of(existing);
        }

        Instant resolutionTime = resolvedAt == null ? Instant.now() : resolvedAt;
        if (resolutionTime.isBefore(existing.startedAt())) {
            throw new IllegalArgumentException("resolvedAt must not be before incident startedAt");
        }

        if (resolutionTime.isBefore(existing.analysisTo())) return Optional.of(existing);
        Instant now = Instant.now();
        return Optional.of(repository.save(new PersistedIncident(
                existing.id(), existing.applicationId(), existing.environment(), PersistedIncident.Status.RESOLVED,
                existing.startedAt(), resolutionTime, existing.originComponent(), existing.originConfidence(),
                existing.severityLevel(), existing.severityScore(), existing.analysisFrom(), existing.analysisTo(),
                existing.analysisSnapshot(), existing.createdAt(), now)));
    }

    /** Recovery may only resolve the exact revision whose successful probe was evaluated. */
    @org.springframework.transaction.annotation.Transactional
    public Optional<PersistedIncident> resolveIfUnchanged(UUID id, Instant expectedUpdate, Instant resolvedAt) {
        var current = repository.findById(id).orElseThrow();
        repository.lockScope(current.applicationId(), current.environment(), current.originComponent());
        current = repository.findById(id).orElseThrow();
        if (!current.updatedAt().equals(expectedUpdate)) return Optional.empty();
        return resolve(id, resolvedAt);
    }

    private boolean hasFailureEvidence(IncidentAnalysis analysis) {
        // The incident lifecycle is outage-only. Latency, HTTP 500s, readiness
        // failures and resource pressure remain telemetry evidence, but they do
        // not create ACTIVE outage incidents while the service is still alive.
        return analysis.origin().evidence().stream().anyMatch(this::isOutageEvidence);
    }

    private boolean isOutageEvidence(EvidenceSignal signal) { return signal.confirmsUnavailable(); }

}
