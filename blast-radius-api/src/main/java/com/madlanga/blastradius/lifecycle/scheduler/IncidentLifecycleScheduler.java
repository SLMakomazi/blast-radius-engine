package com.madlanga.blastradius.lifecycle.scheduler;

import com.madlanga.blastradius.incident.service.IncidentAnalysisService;
import com.madlanga.blastradius.lifecycle.service.IncidentLifecycleService;
import com.madlanga.blastradius.incident.model.IncidentAnalysis;
import com.madlanga.blastradius.incident.model.EvidenceSignal;
import com.madlanga.blastradius.incident.model.PersistedIncident;
import com.madlanga.blastradius.incident.repository.IncidentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled failure detection and guarded recovery checks.
 *
 * Continuously evaluates a recent telemetry window without waiting for a user
 * request. New failure evidence creates or updates an ACTIVE incident. Existing
 * incidents are resolved only after multiple consecutive fully-covered windows
 * show that the incident origin no longer has failure evidence.
 *
 * AI is deliberately absent from this path. Detection, blast-radius calculation,
 * persistence and recovery confirmation remain deterministic.
 */
@Component
@ConditionalOnProperty(name = "blast-radius.lifecycle.auto-recovery-enabled", havingValue = "true", matchIfMissing = true)
public class IncidentLifecycleScheduler {
    private static final Logger log = LoggerFactory.getLogger(IncidentLifecycleScheduler.class);

    private final IncidentAnalysisService analysisService;
    private final IncidentLifecycleService lifecycleService;
    private final IncidentRepository repository;
    private final String applicationId;
    private final String environment;
    private final Duration lookback;
    private final int healthyWindowsRequired;
    private final Map<UUID, Integer> healthyWindows = new ConcurrentHashMap<>();

    public IncidentLifecycleScheduler(
            IncidentAnalysisService analysisService,
            IncidentLifecycleService lifecycleService,
            IncidentRepository repository,
            @Value("${blast-radius.lifecycle.application-id}") String applicationId,
            @Value("${blast-radius.lifecycle.environment:local}") String environment,
            @Value("${blast-radius.lifecycle.recovery-lookback:20s}") Duration lookback,
            @Value("${blast-radius.lifecycle.healthy-windows-required:3}") int healthyWindowsRequired) {
        this.analysisService = analysisService;
        this.lifecycleService = lifecycleService;
        this.repository = repository;
        this.applicationId = applicationId;
        this.environment = environment;
        this.lookback = lookback;
        this.healthyWindowsRequired = Math.max(2, healthyWindowsRequired);
    }

    @Scheduled(
            fixedDelayString = "${blast-radius.lifecycle.evaluation-interval:10s}",
            initialDelayString = "${blast-radius.lifecycle.initial-delay:10s}")
    public void evaluateLifecycle() {
        Instant to = Instant.now();
        Instant from = to.minus(lookback);

        String detectedOrigin = detectAndPersist(from, to);

        List<PersistedIncident> activeIncidents = repository.findActive(applicationId, environment);
        for (PersistedIncident incident : activeIncidents) {
            if (incident.originComponent().equals(detectedOrigin)) {
                healthyWindows.remove(incident.id());
                continue;
            }
            evaluateRecovery(incident, from, to);
        }
    }

    /**
     * Proactively analyzes telemetry with no origin hint. This is the detector:
     * a user/API request is not required to create an incident.
     */
    private String detectAndPersist(Instant from, Instant to) {
        try {
            IncidentAnalysis analysis = analysisService.analyze(
                    applicationId, environment, from, to, null);

            lifecycleService.persistLifecycle(analysis).ifPresent(incident ->
                    log.info(
                            "Proactive failure detected: incident={} origin={} confidence={} severity={} score={}",
                            incident.id(),
                            incident.originComponent(),
                            incident.originConfidence(),
                            incident.severityLevel(),
                            incident.severityScore()));

            return analysis.origin().evidence().stream().anyMatch(com.madlanga.blastradius.incident.model.EvidenceSignal::confirmsUnavailable)
                    ? analysis.origin().component() : null;
        } catch (IllegalStateException e) {
            if (isNoFailureEvidence(e)) {
                log.debug("Proactive detector found no failure evidence in current telemetry window");
                return null;
            }
            log.warn("Proactive detection unavailable ({}); no incident state changed",
                    e.getClass().getSimpleName());
            return null;
        } catch (RuntimeException e) {
            log.warn("Proactive detection failed ({}); no incident state changed",
                    e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Recovery is intentionally stricter than detection. Absence of origin
     * evidence counts as healthy only when all telemetry families are available.
     */
    private void evaluateRecovery(PersistedIncident incident, Instant from, Instant to) {
        try {
            IncidentAnalysis analysis = analysisService.analyze(
                    applicationId, environment, from, to, incident.originComponent());

            var collected = lifecycleService.persistLifecycle(analysis);

            if (!analysis.coverage().isFullyCovered()) {
                healthyWindows.remove(incident.id());
                log.info("Recovery not confirmed for incident {}: telemetry coverage is partial",
                        incident.id());
                return;
            }

            boolean originStillUnavailable = analysis.origin().evidence().stream()
                    .anyMatch(EvidenceSignal::confirmsUnavailable);
            if (originStillUnavailable) {
                healthyWindows.remove(incident.id());
                log.info("Incident {} remains ACTIVE: direct unavailability evidence still exists for origin {}",
                        incident.id(), incident.originComponent());
                return;
            }

            boolean directlyAvailable = analysis.timeline().stream().anyMatch(e -> e.component().equals(incident.originComponent())
                    && e.kind() == com.madlanga.blastradius.incident.model.EvidenceSignal.Kind.AVAILABILITY_AVAILABLE);
            if (!directlyAvailable) { healthyWindows.remove(incident.id()); return; }
            if (collected.isEmpty() || collected.get().analysisTo().isAfter(analysis.to())) {
                healthyWindows.remove(incident.id());
                return;
            }
            confirmHealthyWindow(collected.get(), Instant.now());
        } catch (RuntimeException e) {
            healthyWindows.remove(incident.id());
            log.warn("Recovery evaluation failed for incident {} ({}); leaving incident ACTIVE",
                    incident.id(), e.getClass().getSimpleName());
        }
    }

    private void confirmHealthyWindow(PersistedIncident incident, Instant evaluatedAt) {
        int count = healthyWindows.merge(incident.id(), 1, Integer::sum);
        log.info("Recovery evidence for incident {}: healthy window {}/{}",
                incident.id(), count, healthyWindowsRequired);

        if (count >= healthyWindowsRequired) {
            var resolved = lifecycleService.resolveIfUnchanged(incident.id(), incident.updatedAt(), evaluatedAt);
            healthyWindows.remove(incident.id());
            if (resolved.isPresent() && resolved.get().status() == PersistedIncident.Status.RESOLVED) {
                log.info("Incident {} automatically resolved after {} consecutive healthy windows", incident.id(), count);
            } else {
                log.info("Incident {} recovery confirmation rejected due to a newer revision; will re-evaluate", incident.id());
            }
        }
    }

    private boolean isNoFailureEvidence(IllegalStateException e) {
        return e.getMessage() != null && e.getMessage().startsWith("No failure evidence found");
    }
}
