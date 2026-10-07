package com.madlanga.blastradius.lifecycle.service;

import com.madlanga.blastradius.incident.model.ComponentImpact;
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

    public IncidentLifecycleService(IncidentAnalysisService analysisService, IncidentRepository repository, JsonMapper jsonMapper) {
        this.analysisService = analysisService;
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public IncidentAnalysis analyzeAndPersist(String applicationId, String environment, Instant from, Instant to,
            String originHint) {
        IncidentAnalysis analysis = analysisService.analyze(applicationId, environment, from, to, originHint);
        persistLifecycle(analysis);
        return analysis;
    }

    public Optional<PersistedIncident> persistLifecycle(IncidentAnalysis analysis) {
        if (analysis.origin() == null || analysis.severity() == null) return Optional.empty();

        boolean failureObserved = hasFailureEvidence(analysis);
        Optional<PersistedIncident> active = repository.findActive(
                analysis.applicationId(), analysis.environment(), analysis.origin().component());

        if (!failureObserved) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        String snapshot = snapshot(analysis);

        if (active.isPresent()) {
            PersistedIncident existing = active.get();
            return Optional.of(repository.save(new PersistedIncident(
                    existing.id(), existing.applicationId(), existing.environment(), PersistedIncident.Status.ACTIVE,
                    existing.startedAt(), null, existing.originComponent(), analysis.origin().confidence(),
                    analysis.severity().level(), analysis.severity().score(), analysis.from(), analysis.to(),
                    snapshot, existing.createdAt(), now)));
        }

        Instant startedAt = analysis.timeline().stream()
                .map(EvidenceSignal::timestamp)
                .min(Comparator.naturalOrder())
                .orElse(analysis.from());
        return Optional.of(repository.save(new PersistedIncident(
                UUID.randomUUID(), analysis.applicationId(), analysis.environment(), PersistedIncident.Status.ACTIVE,
                startedAt, null, analysis.origin().component(), analysis.origin().confidence(),
                analysis.severity().level(), analysis.severity().score(), analysis.from(), analysis.to(),
                snapshot, now, now)));
    }

    public Optional<PersistedIncident> resolve(UUID incidentId, Instant resolvedAt) {
        PersistedIncident existing = repository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("incident not found: " + incidentId));
        if (existing.status() == PersistedIncident.Status.RESOLVED) {
            return Optional.of(existing);
        }

        Instant resolutionTime = resolvedAt == null ? Instant.now() : resolvedAt;
        if (resolutionTime.isBefore(existing.startedAt())) {
            throw new IllegalArgumentException("resolvedAt must not be before incident startedAt");
        }

        Instant now = Instant.now();
        return Optional.of(repository.save(new PersistedIncident(
                existing.id(), existing.applicationId(), existing.environment(), PersistedIncident.Status.RESOLVED,
                existing.startedAt(), resolutionTime, existing.originComponent(), existing.originConfidence(),
                existing.severityLevel(), existing.severityScore(), existing.analysisFrom(), existing.analysisTo(),
                existing.analysisSnapshot(), existing.createdAt(), now)));
    }

    private boolean hasFailureEvidence(IncidentAnalysis analysis) {
        // The incident lifecycle is outage-only. Latency, HTTP 500s, readiness
        // failures and resource pressure remain telemetry evidence, but they do
        // not create ACTIVE outage incidents while the service is still alive.
        return analysis.origin().evidence().stream().anyMatch(this::isOutageEvidence);
    }

    private boolean isOutageEvidence(EvidenceSignal signal) {
        String value = signal.signal() == null ? "" : signal.signal().toLowerCase(java.util.Locale.ROOT);
        return value.startsWith("availability health down")
                || value.startsWith("availability health out_of_service")
                || value.startsWith("liveness health down")
                || value.startsWith("liveness health out_of_service")
                || value.startsWith("liveness unreachable")
                // Non-HTTP dependencies such as PostgreSQL do not expose Spring
                // liveness. A dependency error attributed to that topology node
                // is the outage evidence available for the dependency itself.
                || value.startsWith("dependency error observed by")
                || value.startsWith("dependency error inferred from topology");
    }

    private String snapshot(IncidentAnalysis analysis) {
        try {
            return jsonMapper.writeValueAsString(analysis);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize incident analysis snapshot", e);
        }
    }
}
