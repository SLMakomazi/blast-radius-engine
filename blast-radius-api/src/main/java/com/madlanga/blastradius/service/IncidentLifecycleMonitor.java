package com.madlanga.blastradius.service;

import com.madlanga.blastradius.domain.incident.PersistedIncident;
import com.madlanga.blastradius.ports.IncidentRepository;
import java.time.Duration;
import java.time.Instant;
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
 * Phase 10 automatic incident recovery monitor.
 *
 * Re-evaluates each persisted ACTIVE incident against a recent telemetry window.
 * Consecutive windows with no failure evidence confirm recovery. The original
 * incident snapshot remains unchanged when the incident is resolved.
 */
@Component
@ConditionalOnProperty(name = "blast-radius.lifecycle.auto-recovery-enabled", havingValue = "true", matchIfMissing = true)
public class IncidentLifecycleMonitor {
    private static final Logger log = LoggerFactory.getLogger(IncidentLifecycleMonitor.class);

    private final IncidentAnalysisService analysisService;
    private final IncidentLifecycleService lifecycleService;
    private final IncidentRepository repository;
    private final String applicationId;
    private final String environment;
    private final Duration lookback;
    private final int healthyWindowsRequired;
    private final Map<UUID, Integer> healthyWindows = new ConcurrentHashMap<>();

    public IncidentLifecycleMonitor(
            IncidentAnalysisService analysisService,
            IncidentLifecycleService lifecycleService,
            IncidentRepository repository,
            @Value("${blast-radius.lifecycle.application-id:document-platform}") String applicationId,
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
    public void evaluateRecovery() {
        for (PersistedIncident incident : repository.findActive(applicationId, environment)) {
            evaluate(incident);
        }
    }

    private void evaluate(PersistedIncident incident) {
        Instant to = Instant.now();
        Instant from = to.minus(lookback);
        try {
            analysisService.analyze(applicationId, environment, from, to, incident.originComponent(), null);
            healthyWindows.remove(incident.id());
            log.debug("Incident {} still has failure evidence for origin {}", incident.id(), incident.originComponent());
        } catch (IllegalStateException e) {
            if (!isNoFailureEvidence(e)) {
                healthyWindows.remove(incident.id());
                log.warn("Recovery evaluation unavailable for incident {} ({})", incident.id(), e.getClass().getSimpleName());
                return;
            }
            int count = healthyWindows.merge(incident.id(), 1, Integer::sum);
            log.info("Recovery evidence for incident {}: healthy window {}/{}", incident.id(), count, healthyWindowsRequired);
            if (count >= healthyWindowsRequired) {
                lifecycleService.resolve(incident.id(), to);
                healthyWindows.remove(incident.id());
                log.info("Incident {} automatically resolved after {} consecutive healthy windows", incident.id(), count);
            }
        } catch (RuntimeException e) {
            healthyWindows.remove(incident.id());
            log.warn("Recovery evaluation failed for incident {} ({}); leaving incident ACTIVE",
                    incident.id(), e.getClass().getSimpleName());
        }
    }

    private boolean isNoFailureEvidence(IllegalStateException e) {
        return e.getMessage() != null && e.getMessage().startsWith("No failure evidence found");
    }
}
