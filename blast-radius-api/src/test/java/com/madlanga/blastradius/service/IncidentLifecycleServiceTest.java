package com.madlanga.blastradius.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.madlanga.blastradius.domain.incident.*;
import com.madlanga.blastradius.ports.IncidentRepository;
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
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsActiveIncidentWhenFailureEvidenceExists() {
        IncidentAnalysis analysis = analysis(true);
        when(repository.findActive("document-platform", "local", "postgres")).thenReturn(Optional.empty());

        PersistedIncident saved = service.persistLifecycle(analysis).orElseThrow();

        assertThat(saved.status()).isEqualTo(IncidentStatus.ACTIVE);
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
    void healthyFollowUpResolvesExistingIncidentWithoutDeletingHistory() {
        IncidentAnalysis recovered = analysis(false);
        PersistedIncident active = new PersistedIncident(
                java.util.UUID.randomUUID(), "document-platform", "local", IncidentStatus.ACTIVE,
                Instant.parse("2026-10-03T05:20:12Z"), null, "postgres", ConfidenceLevel.HIGH,
                SeverityLevel.HIGH, 50, Instant.parse("2026-10-03T05:20:00Z"),
                Instant.parse("2026-10-03T05:21:00Z"), "{}", Instant.parse("2026-10-03T05:20:12Z"),
                Instant.parse("2026-10-03T05:21:00Z"));
        when(repository.findActive("document-platform", "local", "postgres")).thenReturn(Optional.of(active));

        PersistedIncident saved = service.persistLifecycle(recovered).orElseThrow();

        assertThat(saved.id()).isEqualTo(active.id());
        assertThat(saved.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(saved.startedAt()).isEqualTo(active.startedAt());
        assertThat(saved.resolvedAt()).isEqualTo(recovered.to());
        verify(repository).save(any(PersistedIncident.class));
    }

    private IncidentAnalysis analysis(boolean failure) {
        Instant from = Instant.parse("2026-10-03T05:20:00Z");
        Instant to = Instant.parse("2026-10-03T05:21:00Z");
        EvidenceSignal signal = new EvidenceSignal(
                Instant.parse("2026-10-03T05:20:12Z"), "postgres", "TRACE", "dependency error", "span-1");
        List<EvidenceSignal> evidence = failure ? List.of(signal) : List.of();
        OriginAssessment origin = new OriginAssessment("postgres",
                failure ? ConfidenceLevel.HIGH : ConfidenceLevel.LOW, failure ? 80 : 0, evidence);
        IncidentSeverity severity = new IncidentSeverity(failure ? SeverityLevel.HIGH : SeverityLevel.LOW,
                failure ? 50 : 0, List.of());
        List<ComponentImpact> impacts = List.of(new ComponentImpact(
                "postgres", ObservedState.ORIGIN, 0, List.of("postgres"), evidence));
        return new IncidentAnalysis("document-platform", "local", from, to, origin, null,
                impacts, evidence, severity, null, List.of());
    }
}
