package com.madlanga.blastradius.lifecycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.madlanga.blastradius.incident.model.ComponentImpact;
import com.madlanga.blastradius.incident.model.EvidenceSignal;
import com.madlanga.blastradius.incident.model.IncidentAnalysis;
import com.madlanga.blastradius.incident.model.IncidentSeverity;
import com.madlanga.blastradius.incident.model.OriginAssessment;
import com.madlanga.blastradius.incident.model.PersistedIncident;
import com.madlanga.blastradius.incident.service.IncidentAnalysisService;
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
    void recoveryCollectionPreservesActiveIncidentUntilGuardedResolution() {
        IncidentAnalysis recovered = analysis(false);
        PersistedIncident active = activeIncident();
        when(repository.findActive("document-platform", "local", "postgres")).thenReturn(Optional.of(active));

        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var saved = service.persistLifecycle(recovered).orElseThrow();
        assertThat(saved.status()).isEqualTo(PersistedIncident.Status.ACTIVE);
        assertThat(saved.id()).isEqualTo(active.id());
        assertThat(saved.analysisSnapshot()).contains("availability health DOWN");
    }


    @Test
    void degradationEvidenceDoesNotCreateOutageIncident() {
        Instant from = Instant.parse("2026-10-03T05:20:00Z");
        Instant to = Instant.parse("2026-10-03T05:21:00Z");
        EvidenceSignal latency = new EvidenceSignal(
                Instant.parse("2026-10-03T05:20:12Z"), "document-service", "METRIC",
                "HTTP mean latency 3.000s across 10 requests", "metric-1");
        OriginAssessment origin = new OriginAssessment(
                "document-service", OriginAssessment.Confidence.HIGH, 80, List.of(latency));
        IncidentSeverity severity = new IncidentSeverity(IncidentSeverity.Level.HIGH, 50, List.of());
        IncidentAnalysis analysis = new IncidentAnalysis(
                "document-platform", "local", from, to, origin, null,
                List.of(new ComponentImpact("document-service", ComponentImpact.State.ORIGIN, 0,
                        List.of("document-service"), List.of(latency))),
                List.of(latency), severity, List.of());

        assertThat(service.persistLifecycle(analysis)).isEmpty();
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"HTTP 500", "latency 10s", "readiness health DOWN", "sustained process CPU usage >= 90%", "dependency error observed by caller", "availability health DOWN"})
    void symptomTextAloneCannotCreateOutage(String text) {
        var base = analysis(true);
        var symptom = new EvidenceSignal(base.from(), "postgres", "LOG", text, "symptom");
        var candidate = new IncidentAnalysis(base.applicationId(),base.environment(),base.from(),base.to(),
                new OriginAssessment("postgres", OriginAssessment.Confidence.HIGH,100,List.of(symptom)),
                null,base.impacts(),List.of(symptom),base.severity(),List.of());
        assertThat(service.persistLifecycle(candidate)).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test void repeatedPollsKeepIdentityAndOriginalStart() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var first = service.persistLifecycle(analysis(true)).orElseThrow();
        when(repository.findActive("document-platform", "local", "postgres")).thenReturn(Optional.of(first));
        var second = service.persistLifecycle(analysis(true)).orElseThrow();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.startedAt()).isEqualTo(first.startedAt());
        var tree = JsonMapper.builder().build().readTree(second.analysisSnapshot());
        assertThat(tree.path("timeline").size()).isEqualTo(1);
    }

    @Test void resolvedOriginCannotReopenFromStaleEvidence() {
        when(repository.latestResolution("document-platform", "local", "postgres"))
                .thenReturn(Optional.of(analysis(true).to()));
        assertThat(service.persistLifecycle(analysis(true))).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test void staleHealthyAnalysisCannotOverwriteMoreRecentOutageRevision() {
        PersistedIncident current = activeIncident();
        PersistedIncident newer = new PersistedIncident(
                current.id(), current.applicationId(), current.environment(), current.status(),
                current.startedAt(), current.resolvedAt(), current.originComponent(), current.originConfidence(),
                current.severityLevel(), current.severityScore(), current.analysisFrom(),
                current.analysisTo().plusSeconds(20), current.analysisSnapshot(), current.createdAt(),
                current.updatedAt().plusSeconds(20));
        when(repository.findActive("document-platform", "local", "postgres"))
                .thenReturn(Optional.of(newer));
        PersistedIncident result = service.persistLifecycle(analysis(false)).orElseThrow();
        assertThat(result).isSameAs(newer);
        verify(repository, never()).save(any());
    }

    @Test void recoveryCannotResolveARevisionUpdatedByAnotherCollector() {
        var current = activeIncident();
        when(repository.findById(current.id())).thenReturn(Optional.of(current));
        assertThat(service.resolveIfUnchanged(current.id(),current.updatedAt().minusSeconds(1),Instant.now())).isEmpty();
        verify(repository,never()).save(any());
    }

    private PersistedIncident activeIncident() {
        return new PersistedIncident(
                java.util.UUID.randomUUID(), "document-platform", "local", PersistedIncident.Status.ACTIVE,
                Instant.parse("2026-10-03T05:20:12Z"), null, "postgres", OriginAssessment.Confidence.HIGH,
                IncidentSeverity.Level.HIGH, 50, Instant.parse("2026-10-03T05:20:00Z"),
                Instant.parse("2026-10-03T05:21:00Z"), JsonMapper.builder().build().writeValueAsString(analysis(true)),
                Instant.parse("2026-10-03T05:20:12Z"), Instant.parse("2026-10-03T05:21:00Z"));
    }

    private IncidentAnalysis analysis(boolean failure) {
        Instant from = Instant.parse("2026-10-03T05:20:00Z");
        Instant to = Instant.parse("2026-10-03T05:21:00Z");
        EvidenceSignal signal = new EvidenceSignal(
                Instant.parse("2026-10-03T05:20:12Z"), "postgres", "HEALTH", "availability health DOWN", "probe-1", EvidenceSignal.Kind.AVAILABILITY_UNAVAILABLE, "test-probe", "database/availability", from);
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
