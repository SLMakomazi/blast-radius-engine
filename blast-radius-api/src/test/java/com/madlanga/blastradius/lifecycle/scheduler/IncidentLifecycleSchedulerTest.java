package com.madlanga.blastradius.lifecycle.scheduler;

import com.madlanga.blastradius.incident.model.*;
import com.madlanga.blastradius.incident.repository.IncidentRepository;
import com.madlanga.blastradius.incident.service.IncidentAnalysisService;
import com.madlanga.blastradius.lifecycle.service.IncidentLifecycleService;
import com.madlanga.blastradius.telemetry.model.TelemetryCoverage;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IncidentLifecycleSchedulerTest {
    @Test void resolvesOnlyAfterConsecutivePositiveAvailabilityChecksAndCollectsRecoveryEvidence() {
        var engine = mock(IncidentAnalysisService.class);
        var lifecycle = mock(IncidentLifecycleService.class);
        var repository = mock(IncidentRepository.class);
        Instant start = Instant.now().minusSeconds(120);
        UUID id = UUID.randomUUID();
        var incident = new PersistedIncident(id,"ledger","test",PersistedIncident.Status.ACTIVE,start,null,"db",
                OriginAssessment.Confidence.HIGH,IncidentSeverity.Level.HIGH,50,start,start,"{}",start,start);
        when(repository.findActive("ledger","test")).thenReturn(List.of(incident));
        when(engine.analyze(eq("ledger"),eq("test"),any(),any(),isNull()))
                .thenThrow(new IllegalStateException("No failure evidence found"));
        var up = new EvidenceSignal(Instant.now(),"db","HEALTH","availability check UP","probe",
                EvidenceSignal.Kind.AVAILABILITY_AVAILABLE,"jdbc","availability",Instant.now());
        var origin = new OriginAssessment("db",OriginAssessment.Confidence.LOW,0,List.of());
        var noProbe = new IncidentAnalysis("ledger","test",start,Instant.now(),origin,TelemetryCoverage.allAvailable(),
                List.of(),List.of(),new IncidentSeverity(IncidentSeverity.Level.LOW,0,List.of()),List.of());
        var healthy = new IncidentAnalysis("ledger","test",start,Instant.now(),origin,TelemetryCoverage.allAvailable(),
                List.of(),List.of(up),noProbe.severity(),List.of());
        when(engine.analyze(eq("ledger"),eq("test"),any(),any(),eq("db"))).thenReturn(noProbe,healthy,freshProbe(healthy,1),freshProbe(healthy,2));
        when(lifecycle.persistLifecycle(any())).thenReturn(Optional.of(incident));
        var scheduler = new IncidentLifecycleScheduler(engine,lifecycle,repository,"ledger","test",Duration.ofSeconds(20),3);
        scheduler.evaluateLifecycle(); scheduler.evaluateLifecycle(); scheduler.evaluateLifecycle();
        verify(lifecycle,never()).resolveIfUnchanged(any(),any(),any());
        scheduler.evaluateLifecycle();
        verify(lifecycle).resolveIfUnchanged(eq(id),eq(incident.updatedAt()),any());
        verify(lifecycle,times(4)).persistLifecycle(any());
    }
    @Test void lingeringHttpErrorsDoNotPreventRecoveryAfterDirectAvailabilityReturns() {
        var engine = mock(IncidentAnalysisService.class);
        var lifecycle = mock(IncidentLifecycleService.class);
        var repository = mock(IncidentRepository.class);
        Instant start = Instant.now().minusSeconds(120);
        UUID id = UUID.randomUUID();
        var incident = new PersistedIncident(id,"ledger","test",PersistedIncident.Status.ACTIVE,start,null,"db",
                OriginAssessment.Confidence.HIGH,IncidentSeverity.Level.HIGH,50,start,start,"{}",start,start);
        when(repository.findActive("ledger","test")).thenReturn(List.of(incident));
        when(engine.analyze(eq("ledger"),eq("test"),any(),any(),isNull()))
                .thenThrow(new IllegalStateException("No failure evidence found"));
        var up = new EvidenceSignal(Instant.now(),"db","HEALTH","availability check UP","probe",
                EvidenceSignal.Kind.AVAILABILITY_AVAILABLE,"jdbc","availability",Instant.now());
        var symptom = new EvidenceSignal(Instant.now(),"db","METRIC","HTTP 500 counter increased","metric",
                EvidenceSignal.Kind.SYMPTOM,"prometheus","counter",Instant.now());
        var origin = new OriginAssessment("db",OriginAssessment.Confidence.LOW,0,List.of(symptom));
        var healthy = new IncidentAnalysis("ledger","test",start,Instant.now(),origin,TelemetryCoverage.allAvailable(),
                List.of(),List.of(up,symptom),new IncidentSeverity(IncidentSeverity.Level.LOW,0,List.of()),List.of());
        when(engine.analyze(eq("ledger"),eq("test"),any(),any(),eq("db"))).thenReturn(healthy,freshProbe(healthy,1),freshProbe(healthy,2));
        when(lifecycle.persistLifecycle(any())).thenReturn(Optional.of(incident));
        var scheduler = new IncidentLifecycleScheduler(engine,lifecycle,repository,"ledger","test",Duration.ofSeconds(20),3);
        scheduler.evaluateLifecycle(); scheduler.evaluateLifecycle(); scheduler.evaluateLifecycle();
        verify(lifecycle).resolveIfUnchanged(eq(id),eq(incident.updatedAt()),any());
    }

    @Test void cachedPositiveProbeCannotCountTowardRecoveryMoreThanOnce() {
        var engine = mock(IncidentAnalysisService.class);
        var lifecycle = mock(IncidentLifecycleService.class);
        var repository = mock(IncidentRepository.class);
        Instant start = Instant.now().minusSeconds(120);
        var incident = new PersistedIncident(UUID.randomUUID(),"ledger","test",PersistedIncident.Status.ACTIVE,
                start,null,"db",OriginAssessment.Confidence.HIGH,IncidentSeverity.Level.HIGH,
                50,start,start,"{}",start,start);
        when(repository.findActive("ledger","test")).thenReturn(List.of(incident));
        when(engine.analyze(eq("ledger"),eq("test"),any(),any(),isNull()))
                .thenThrow(new IllegalStateException("No failure evidence found"));
        var positive = new EvidenceSignal(Instant.now(),"db","HEALTH","availability check UP","same-probe",
                EvidenceSignal.Kind.AVAILABILITY_AVAILABLE,"jdbc","availability",Instant.now());
        var analysis = new IncidentAnalysis("ledger","test",start,Instant.now(),
                new OriginAssessment("db",OriginAssessment.Confidence.LOW,0,List.of()),
                TelemetryCoverage.allAvailable(),List.of(),List.of(positive),
                new IncidentSeverity(IncidentSeverity.Level.LOW,0,List.of()),List.of());
        when(engine.analyze(eq("ledger"),eq("test"),any(),any(),eq("db"))).thenReturn(analysis);
        when(lifecycle.persistLifecycle(any())).thenReturn(Optional.of(incident));
        var scheduler = new IncidentLifecycleScheduler(engine,lifecycle,repository,"ledger","test",Duration.ofSeconds(20),3);
        scheduler.evaluateLifecycle(); scheduler.evaluateLifecycle(); scheduler.evaluateLifecycle();
        verify(lifecycle,never()).resolveIfUnchanged(any(),any(),any());
    }

    @Test void olderPositiveEvidenceCannotResolveNewerRetainedDirectFailure() {
        var engine = mock(IncidentAnalysisService.class);
        var lifecycle = mock(IncidentLifecycleService.class);
        var repository = mock(IncidentRepository.class);
        Instant start = Instant.now().minusSeconds(120);
        Instant failureAt = start.plusSeconds(90);
        var incident = new PersistedIncident(UUID.randomUUID(),"ledger","test",PersistedIncident.Status.ACTIVE,
                start,null,"db",OriginAssessment.Confidence.HIGH,IncidentSeverity.Level.HIGH,
                50,start,start.plusSeconds(100),"{}",start,start);
        when(repository.findActive("ledger","test")).thenReturn(List.of(incident));
        when(engine.analyze(eq("ledger"),eq("test"),any(),any(),isNull()))
                .thenThrow(new IllegalStateException("No failure evidence found"));
        var initialUp = new EvidenceSignal(start.plusSeconds(10),"db","HEALTH","availability health UP","up",
                EvidenceSignal.Kind.AVAILABILITY_AVAILABLE,"jdbc","availability",start.plusSeconds(11));
        var data = new IncidentAnalysis("ledger","test",start,start.plusSeconds(100),
                new OriginAssessment("db",OriginAssessment.Confidence.LOW,0,List.of()),
                TelemetryCoverage.allAvailable(),List.of(),List.of(initialUp),
                new IncidentSeverity(IncidentSeverity.Level.LOW,0,List.of()),List.of());
        when(engine.analyze(eq("ledger"),eq("test"),any(),any(),eq("db")))
                .thenReturn(data,freshProbe(data,1),freshProbe(data,2));
        var snapshot = "{\"timeline\":[{\"timestamp\":\""+failureAt+"\",\"component\":\"db\",\"family\":\"HEALTH\",\"kind\":\"AVAILABILITY_UNAVAILABLE\"}]}";
        var stored = new PersistedIncident(incident.id(),incident.applicationId(),incident.environment(),
                incident.status(),incident.startedAt(),null,incident.originComponent(),incident.originConfidence(),
                incident.severityLevel(),incident.severityScore(),incident.analysisFrom(),incident.analysisTo(),
                snapshot,incident.createdAt(),incident.updatedAt());
        when(lifecycle.persistLifecycle(any())).thenReturn(Optional.of(stored));
        var scheduler = new IncidentLifecycleScheduler(engine,lifecycle,repository,"ledger","test",Duration.ofSeconds(20),3);
        scheduler.evaluateLifecycle(); scheduler.evaluateLifecycle(); scheduler.evaluateLifecycle();
        verify(lifecycle,never()).resolveIfUnchanged(any(),any(),any());
    }

    private IncidentAnalysis freshProbe(IncidentAnalysis original,int offsetSeconds) {
        var events = original.timeline().stream().map(e -> e.kind() == EvidenceSignal.Kind.AVAILABILITY_AVAILABLE
                ? new EvidenceSignal(e.timestamp().plusSeconds(offsetSeconds),e.component(),e.family(),e.signal(),
                        e.evidenceId()+"-"+offsetSeconds,e.kind(),e.provider(),e.sourceRef(),
                        e.collectedAt().plusSeconds(offsetSeconds))
                : e).toList();
        return new IncidentAnalysis(original.applicationId(),original.environment(),
                original.from(),original.to(),original.origin(),original.coverage(),original.impacts(),
                events,original.severity(),original.warnings());
    }
}
