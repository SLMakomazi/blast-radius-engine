package com.madlanga.blastradius.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.madlanga.blastradius.domain.evidence.*;
import com.madlanga.blastradius.domain.incident.*;
import com.madlanga.blastradius.ports.TelemetryProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncidentAnalysisServiceTest {
    private static final Instant FROM=Instant.parse("2026-10-02T12:00:00Z");
    private static final Instant TO=Instant.parse("2026-10-02T12:05:00Z");

    @Test
    void findsDeepestFailingDependencyAndClassifiesObservedRadius() {
        TelemetryBundle bundle=bundle(TelemetryCoverage.allAvailable());
        IncidentAnalysis result=new IncidentAnalysisService(q -> bundle)
                .analyze("document-platform","local",FROM,TO,null);

        assertThat(result.origin().component()).isEqualTo("postgres");
        assertThat(result.origin().confidence()).isNotNull();
        assertThat(result.impacts()).extracting(ComponentImpact::component)
                .containsExactly("postgres","document-service","customer-service","payment-service");
        assertThat(result.impacts()).allMatch(i -> i.state()==ObservedState.ORIGIN || i.state()==ObservedState.OBSERVED);
        assertThat(result.timeline()).isSortedAccordingTo(java.util.Comparator.comparing(EvidenceSignal::timestamp)
                .thenComparing(EvidenceSignal::component).thenComparing(EvidenceSignal::family));
    }

    @Test
    void marksUnobservedTheoreticalNodeUnknownWhenCoverageIsPartial() {
        TelemetryCoverage partial=TelemetryCoverage.builder().logs(CoverageStatus.UNAVAILABLE)
                .metrics(CoverageStatus.AVAILABLE).traces(CoverageStatus.AVAILABLE)
                .health(CoverageStatus.PARTIAL).build();
        TelemetryBundle partialBundle=TelemetryBundle.builder().coverage(partial).spans(List.of(
                span("p","payment-service","customer-service",SpanStatus.OK,FROM.plusSeconds(4),Map.of()),
                span("c","customer-service","document-service",SpanStatus.ERROR,FROM.plusSeconds(3),Map.of()),
                span("d","document-service","postgres",SpanStatus.ERROR,FROM.plusSeconds(2),Map.of("db.system.name","postgresql"))
        )).health(List.of(health("document-service",HealthState.DOWN,FROM.plusSeconds(2)))).build();

        IncidentAnalysis result=new IncidentAnalysisService(q -> partialBundle)
                .analyze("document-platform","local",FROM,TO,"postgres");

        assertThat(result.impacts().stream().filter(i->i.component().equals("payment-service")).findFirst().orElseThrow().state())
                .isEqualTo(ObservedState.UNKNOWN);
        assertThat(result.warnings()).anyMatch(w -> w.contains("missing evidence is not healthy evidence"));
    }

    private TelemetryBundle bundle(TelemetryCoverage coverage) {
        return TelemetryBundle.builder().coverage(coverage).spans(List.of(
                span("p","payment-service","customer-service",SpanStatus.ERROR,FROM.plusSeconds(4),Map.of()),
                span("c","customer-service","document-service",SpanStatus.ERROR,FROM.plusSeconds(3),Map.of()),
                span("d","document-service","postgres",SpanStatus.ERROR,FROM.plusSeconds(2),Map.of("db.system.name","postgresql"))
        )).health(List.of(health("document-service",HealthState.DOWN,FROM.plusSeconds(2)))).build();
    }

    private SpanEvidence span(String id,String service,String peer,SpanStatus status,Instant at,Map<String,String> attrs){
        return SpanEvidence.builder().id(id).traceId("0123456789abcdef0123456789abcdef").spanId(id)
                .service(service).environment("local").operation("call "+peer).startTime(at).durationMs(10)
                .status(status).peerService(peer).attributes(attrs)
                .provenance(provenance(EvidenceFamily.TRACES)).build();
    }
    private HealthEvidence health(String service,HealthState state,Instant at){
        return HealthEvidence.builder().id("h-"+service).timestamp(at).service(service).environment("local")
                .endpoint("/actuator/health/readiness").state(state).provenance(provenance(EvidenceFamily.HEALTH)).build();
    }
    private EvidenceProvenance provenance(EvidenceFamily family){
        return EvidenceProvenance.of(family,"test",TO,"fixture");
    }
}
