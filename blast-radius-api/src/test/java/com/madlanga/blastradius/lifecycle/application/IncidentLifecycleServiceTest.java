package com.madlanga.blastradius.lifecycle.application;

import com.madlanga.blastradius.incident.service.IncidentAnalysisService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.madlanga.blastradius.incident.model.*;
import com.madlanga.blastradius.incident.repository.IncidentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class IncidentLifecycleServiceTest {
    @Mock IncidentAnalysisService analysisService;
    @Mock IncidentRepository repository;

    private IncidentLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new IncidentLifecycleService(analysisService, repository, JsonMapper.builder().build());
    }

    @Test
    void createsActiveIncidentWhenFailureEvidenceExists() {
        IncidentAnalysis analysis = analysis(true);
        when(repository.findActive("document-platform", "local", "postgres")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersistedIncident saved = service.persistLifecycle(analysis).orElseThrow();

        assertThat(saved.status()).isEqualTo(PersistedIncident.Status.ACTIVE);
        assertThat(saved.originComponent()).isEqualTo("postgres");
        assertThat(saved.resolvedAt()).isNull();
        assertThat(saved.analysisSnapshot()).contains("document-platform");
        verify(repository).save(any(PersistedIncident.class));
    }

    @Test
    void theoreticalAnalysisDoesNotCreateFalseIncident() {
        IncidentAnalysis analysis = analysis(false);
        when(repository.findActive("document-platform", "local", "postgres")).thenReturn(Optional.empty());

        assertThat(service.persistLifecycle(analysis)).isEmpty();

        verify(repository, never()).save(any());
    }

    @Test
    void noFailureEvidenceDoesNotResolveExistingIncident() {
        IncidentAnalysis recovered = analysis(false);
        PersistedIncident active = activeIncident();
        when(repository.findActive("document-platform", "local", "postgres")).thenReturn(Optional.of(active));

        assertThat(service.persistLifecycle(recovered)).isEmpty();

        verify(repository, never()).save(any());
    }

    @Test
    void explicitResolutionPreservesPeakSeverityAndIncidentHistory() {
        PersistedIncident active = activeIncident();
        Instant resolvedAt = Instant.parse("2026-10-03T05:30:00Z");
        when(repository.findById(active.id())).thenReturn(Optional.of(active));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersistedIncident saved = service.resolve(active.id(), resolvedAt).orElseThrow();

        assertThat(saved.id()).isEqualTo(active.id());
        assertThat(saved.status()).isEqualTo(PersistedIncident.Status.RESOLVED);
        assertThat(saved.startedAt()).isEqualTo(active.startedAt());
        assertThat(saved.resolvedAt()).isEqualTo(resolvedAt);
        assertThat(saved.severityLevel()).isEqualTo(IncidentSeverity.Level.HIGH);
        assertThat(saved.severityScore()).isEqualTo(50);
        assertThat(saved.analysisSnapshot()).isEqualTo(active.analysisSnapshot());
    }

    private PersistedIncident activeIncident() {
        return new PersistedIncident(
                java.util.UUID.randomUUID(), "document-platform", "local", PersistedIncident.Status.ACTIVE,
                Instant.parse("2026-10-03T05:20:12Z"), null, "postgres", OriginAssessment.Confidence.HIGH,
                IncidentSeverity.Level.HIGH, 50, Instant.parse("2026-10-03T05:20:00Z"),
                Instant.parse("2026-10-03T05:21:00Z"), "{\"peak\":true}",
                Instant.parse("2026-10-03T05:20:12Z"), Instant.parse("2026-10-03T05:21:00Z"));
    }

    private IncidentAnalysis analysis(boolean failure) {
        Instant from = Instant.parse("2026-10-03T05:20:00Z");
        Instant to = Instant.parse("2026-10-03T05:21:00Z");
        EvidenceSignal signal = new EvidenceSignal(
                Instant.parse("2026-10-03T05:20:12Z"), "postgres", "TRACE", "dependency error", "span-1");
        List<EvidenceSignal> evidence = failure ? List.of(signal) : List.of();
        OriginAssessment origin = new OriginAssessment("postgres",
                failure ? OriginAssessment.Confidence.HIGH : OriginAssessment.Confidence.LOW, failure ? 80 : 0, evidence);
        IncidentSeverity severity = new IncidentSeverity(failure ? IncidentSeverity.Level.HIGH : IncidentSeverity.Level.LOW,
                failure ? 50 : 0, List.of());
        List<ComponentImpact> impacts = List.of(new ComponentImpact(
                "postgres", ComponentImpact.State.ORIGIN, 0, List.of("postgres"), evidence));
        return new IncidentAnalysis("document-platform", "local", from, to, origin, null,
                impacts, evidence, severity, List.of());
    }
}
