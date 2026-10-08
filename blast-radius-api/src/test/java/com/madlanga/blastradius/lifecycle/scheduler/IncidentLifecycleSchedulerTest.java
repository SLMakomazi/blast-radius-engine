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
        when(engine.analyze(eq("ledger"),eq("test"),any(),any(),eq("db"))).thenReturn(noProbe,healthy,healthy,healthy);
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
        when(engine.analyze(eq("ledger"),eq("test"),any(),any(),eq("db"))).thenReturn(healthy);
        when(lifecycle.persistLifecycle(any())).thenReturn(Optional.of(incident));
        var scheduler = new IncidentLifecycleScheduler(engine,lifecycle,repository,"ledger","test",Duration.ofSeconds(20),3);
        scheduler.evaluateLifecycle(); scheduler.evaluateLifecycle(); scheduler.evaluateLifecycle();
        verify(lifecycle).resolveIfUnchanged(eq(id),eq(incident.updatedAt()),any());
    }

}
