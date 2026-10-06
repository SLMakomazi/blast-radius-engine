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
        IncidentAnalysis result=service(q -> bundle)
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
    void usesPreIncidentTraceHistoryToRecoverDependencyMissingFromOutageWindow() {
        TelemetryBundle incident = TelemetryBundle.builder()
                .coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(
                        span("p-fail","payment-service","customer-service",SpanStatus.ERROR,FROM.plusSeconds(4),Map.of()),
                        span("c-fail","customer-service","document-service",SpanStatus.ERROR,FROM.plusSeconds(3),Map.of()),
                        span("jdbc-fail","document-service",null,SpanStatus.ERROR,FROM.plusSeconds(2),Map.of())))
                .health(List.of(health("document-service",HealthState.DOWN,FROM.plusSeconds(2))))
                .build();

        TelemetryBundle history = TelemetryBundle.builder()
                .coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(
                        span("p-ok","payment-service","customer-service",SpanStatus.OK,FROM.minusSeconds(30),Map.of()),
                        span("c-ok","customer-service","document-service",SpanStatus.OK,FROM.minusSeconds(30),Map.of()),
                        span("db-ok","document-service","postgres",SpanStatus.OK,FROM.minusSeconds(30),
                                Map.of("db.system.name","postgresql"))))
                .build();

        TelemetryProvider provider = query -> query.getTo().equals(FROM) ? history : incident;

        IncidentAnalysis result = service(provider)
                .analyze("document-platform","local",FROM,TO,null);

        assertThat(result.origin().component()).isEqualTo("postgres");
        assertThat(result.impacts()).extracting(ComponentImpact::component)
                .containsExactly("postgres","document-service","customer-service","payment-service");
        assertThat(result.origin().evidence()).anyMatch(signal ->
                signal.family().equals("TRACE")
                        && signal.signal().contains("dependency error inferred from topology")
                        && signal.signal().contains("document-service"));
        assertThat(result.timeline()).noneMatch(signal -> signal.evidenceId().equals("db-ok"));
    }

    @Test
    void infersPeerlessFailedDependencyWhenTopologyHasSingleCandidate() {
        TelemetryBundle bundle=TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable()).spans(List.of(
                span("p","payment-service","customer-service",SpanStatus.ERROR,FROM.plusSeconds(4),Map.of()),
                span("c","customer-service","document-service",SpanStatus.ERROR,FROM.plusSeconds(3),Map.of()),
                span("topology-db","document-service","postgres",SpanStatus.OK,FROM.plusSeconds(1),Map.of("db.system","postgresql")),
                span("failed-jdbc","document-service",null,SpanStatus.ERROR,FROM.plusSeconds(2),Map.of())
        )).health(List.of(health("document-service",HealthState.DOWN,FROM.plusSeconds(2)))).build();

        IncidentAnalysis result=service(q -> bundle)
                .analyze("document-platform","local",FROM,TO,null);

        assertThat(result.origin().component()).isEqualTo("postgres");
        assertThat(result.origin().evidence()).anyMatch(signal ->
                signal.family().equals("TRACE")
                        && signal.signal().contains("dependency error inferred from topology")
                        && signal.signal().contains("document-service"));
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

        IncidentAnalysis result=service(q -> partialBundle)
                .analyze("document-platform","local",FROM,TO,"postgres");

        assertThat(result.impacts().stream().filter(i->i.component().equals("payment-service")).findFirst().orElseThrow().state())
                .isEqualTo(ObservedState.UNKNOWN);
        assertThat(result.warnings()).anyMatch(w -> w.contains("missing evidence is not healthy evidence"));
    }


    @Test
    void fullCoverageDistinguishesTheoreticalOnlyFromObserved() {
        TelemetryBundle bundle=TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable()).spans(List.of(
                span("p","payment-service","customer-service",SpanStatus.OK,FROM.plusSeconds(4),Map.of()),
                span("c","customer-service","document-service",SpanStatus.ERROR,FROM.plusSeconds(3),Map.of()),
                span("d","document-service","postgres",SpanStatus.ERROR,FROM.plusSeconds(2),Map.of("db.system.name","postgresql"))
        )).build();
        IncidentAnalysis result=service(q -> bundle)
                .analyze("document-platform","local",FROM,TO,"postgres");
        assertThat(result.impacts().stream().filter(i->i.component().equals("payment-service")).findFirst().orElseThrow().state())
                .isEqualTo(ObservedState.THEORETICAL_ONLY);
    }

    @Test
    void reportsFailureEvidenceOutsideTheoreticalRadiusAsUnexpected() {
        TelemetryBundle base=bundle(TelemetryCoverage.allAvailable());
        TelemetryBundle withUnexpected=TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(base.getSpans()).health(base.getHealth())
                .logs(List.of(log("blast-radius-api","ERROR",FROM.plusSeconds(5)))).build();
        IncidentAnalysis result=service(q -> withUnexpected)
                .analyze("document-platform","local",FROM,TO,"postgres");
        assertThat(result.impacts()).anyMatch(i -> i.component().equals("blast-radius-api")
                && i.state()==ObservedState.UNEXPECTED);
    }

    @Test
    void counterMetricMustIncreaseInsideWindowBeforeItCountsAsFailureEvidence() {
        TelemetryBundle metricsOnly=TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(
                        span("p","payment-service","customer-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("c","customer-service","document-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("d","document-service","postgres",SpanStatus.OK,FROM.plusSeconds(1),Map.of("db.system.name","postgresql"))))
                .metrics(List.of(metric("m1","payment-service",5,FROM.plusSeconds(1)),
                        metric("m2","payment-service",5,FROM.plusSeconds(30)))).build();
        IncidentAnalysis result=service(q -> metricsOnly)
                .analyze("document-platform","local",FROM,TO,"postgres");
        assertThat(result.impacts().stream().filter(i->i.component().equals("payment-service")).findFirst().orElseThrow().state())
                .isEqualTo(ObservedState.THEORETICAL_ONLY);
    }

    @Test
    void rejectsOriginHintThatIsNotInDiscoveredTopology() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service(q -> bundle(TelemetryCoverage.allAvailable()))
                .analyze("document-platform","local",FROM,TO,"missing-service")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not present in discovered topology");
    }


    @Test
    void ignoresHttpFiveXxLatencySumEvenWhenItIncreases() {
        TelemetryBundle metricsOnly=TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(
                        span("p","payment-service","customer-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("c","customer-service","document-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("d","document-service","postgres",SpanStatus.OK,FROM.plusSeconds(1),Map.of("db.system.name","postgresql"))))
                .metrics(List.of(
                        metricNamed("s1","payment-service","http.server.requests.seconds.sum",100,FROM.plusSeconds(1),Map.of("status","500")),
                        metricNamed("s2","payment-service","http.server.requests.seconds.sum",250,FROM.plusSeconds(30),Map.of("status","500"))))
                .build();
        IncidentAnalysis result=service(q -> metricsOnly)
                .analyze("document-platform","local",FROM,TO,"postgres");
        assertThat(result.impacts().stream().filter(i->i.component().equals("payment-service")).findFirst().orElseThrow().state())
                .isEqualTo(ObservedState.THEORETICAL_ONLY);
    }

    @Test
    void timeoutCounterAloneIsSupportingNotSufficientFailureEvidence() {
        TelemetryBundle metricsOnly=TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(
                        span("p","payment-service","customer-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("c","customer-service","document-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("d","document-service","postgres",SpanStatus.OK,FROM.plusSeconds(1),Map.of("db.system.name","postgresql"))))
                .metrics(List.of(
                        metricNamed("t1","document-service","hikaricp.connections.timeout.total",1,FROM.plusSeconds(1),Map.of()),
                        metricNamed("t2","document-service","hikaricp.connections.timeout.total",5,FROM.plusSeconds(30),Map.of())))
                .build();
        IncidentAnalysis result=service(q -> metricsOnly)
                .analyze("document-platform","local",FROM,TO,"postgres");
        assertThat(result.impacts().stream().filter(i->i.component().equals("document-service")).findFirst().orElseThrow().state())
                .isEqualTo(ObservedState.THEORETICAL_ONLY);
    }

    @Test
    void acceptsIncreasingFiveXxCountAsFailureEvidence() {
        TelemetryBundle metricsOnly=TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(
                        span("p","payment-service","customer-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("c","customer-service","document-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("d","document-service","postgres",SpanStatus.OK,FROM.plusSeconds(1),Map.of("db.system.name","postgresql"))))
                .metrics(List.of(
                        metricNamed("e1","payment-service","http.server.requests.seconds.count",5,FROM.plusSeconds(1),Map.of("status","500")),
                        metricNamed("e2","payment-service","http.server.requests.seconds.count",7,FROM.plusSeconds(30),Map.of("status","500"))))
                .build();
        IncidentAnalysis result=service(q -> metricsOnly)
                .analyze("document-platform","local",FROM,TO,"postgres");
        assertThat(result.impacts().stream().filter(i->i.component().equals("payment-service")).findFirst().orElseThrow().state())
                .isEqualTo(ObservedState.OBSERVED);
    }

    @Test
    void acceptsHighMeanHttpLatencyAsDegradationEvidence() {
        TelemetryBundle metricsOnly=TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(
                        span("p","payment-service","customer-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("c","customer-service","document-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("d","document-service","postgres",SpanStatus.OK,FROM.plusSeconds(1),Map.of("db.system.name","postgresql"))))
                .metrics(List.of(
                        metricNamed("c1","document-service","http.server.requests.seconds.count",10,FROM.plusSeconds(1),Map.of("status","201")),
                        metricNamed("c2","document-service","http.server.requests.seconds.count",12,FROM.plusSeconds(30),Map.of("status","201")),
                        metricNamed("s1","document-service","http.server.requests.seconds.sum",1,FROM.plusSeconds(1),Map.of("status","201")),
                        metricNamed("s2","document-service","http.server.requests.seconds.sum",11,FROM.plusSeconds(30),Map.of("status","201"))))
                .build();

        IncidentAnalysis result=service(q -> metricsOnly)
                .analyze("document-platform","local",FROM,TO,"document-service");

        assertThat(result.origin().evidence()).anyMatch(signal ->
                signal.family().equals("METRIC") && signal.signal().contains("HTTP mean latency"));
    }

    @Test
    void acceptsSustainedCpuPressureAsDegradationEvidence() {
        TelemetryBundle metricsOnly=TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(
                        span("p","payment-service","customer-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("c","customer-service","document-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("d","document-service","postgres",SpanStatus.OK,FROM.plusSeconds(1),Map.of("db.system.name","postgresql"))))
                .metrics(List.of(
                        metricNamed("cpu1","document-service","process.cpu.usage",0.94,FROM.plusSeconds(1),Map.of()),
                        metricNamed("cpu2","document-service","process.cpu.usage",0.96,FROM.plusSeconds(30),Map.of())))
                .build();

        IncidentAnalysis result=service(q -> metricsOnly)
                .analyze("document-platform","local",FROM,TO,"document-service");

        assertThat(result.origin().evidence()).anyMatch(signal ->
                signal.family().equals("METRIC") && signal.signal().contains("CPU"));
    }

    @Test
    void acceptsSustainedDatabasePoolContentionAsDegradationEvidence() {
        TelemetryBundle metricsOnly=TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(
                        span("p","payment-service","customer-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("c","customer-service","document-service",SpanStatus.OK,FROM.plusSeconds(1),Map.of()),
                        span("d","document-service","postgres",SpanStatus.OK,FROM.plusSeconds(1),Map.of("db.system.name","postgresql"))))
                .metrics(List.of(
                        metricNamed("pool1","document-service","hikaricp.connections.pending",2,FROM.plusSeconds(1),Map.of()),
                        metricNamed("pool2","document-service","hikaricp.connections.pending",3,FROM.plusSeconds(30),Map.of())))
                .build();

        IncidentAnalysis result=service(q -> metricsOnly)
                .analyze("document-platform","local",FROM,TO,"document-service");

        assertThat(result.origin().evidence()).anyMatch(signal ->
                signal.family().equals("METRIC") && signal.signal().contains("connection-pool contention"));
    }

    @Test
    void excludesHistoricalFailuresFromEveryEvidenceFamilyAndKeepsCoverageIndependent() {
        var healthy = TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(span("known-edge","api","database",SpanStatus.OK,FROM.plusSeconds(1),Map.of()))).build();
        var supplied = TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(span("old-error","api","database",SpanStatus.ERROR,FROM.minusSeconds(1),Map.of())))
                .logs(List.of(log("api","ERROR",FROM.minusSeconds(1))))
                .health(List.of(health("api",HealthState.DOWN,FROM.minusSeconds(1))))
                .metrics(List.of(metric("old-1","api",1,FROM.minusSeconds(30)), metric("old-2","api",5,FROM.minusSeconds(1))))
                .build();
        var topology = new com.madlanga.blastradius.adapters.topology.TraceDiscoveredTopologyProvider(null)
                .discover("test","local",healthy.getSpans());
        var service = new IncidentAnalysisService(q -> supplied, (a,e) -> topology);
        var result = service.analyze("test","local",FROM,TO,"database");
        assertThat(result.timeline()).isEmpty();
        assertThat(result.origin().evidenceScore()).isZero();
        assertThat(result.coverage().isFullyCovered()).isTrue();
        assertThat(result.impacts().getLast().state()).isEqualTo(ObservedState.THEORETICAL_ONLY);
    }

    @Test
    void serverFailureAloneDoesNotAccuseRetainedDependency() {
        var topology = new com.madlanga.blastradius.adapters.topology.TraceDiscoveredTopologyProvider(null)
                .discover("test","local",List.of(span("old","api","database",SpanStatus.OK,FROM.minusSeconds(5),Map.of())));
        var serverError = SpanEvidence.builder().id("server").traceId("trace").spanId("server").service("api")
                .environment("local").startTime(FROM.plusSeconds(1)).kind(SpanKind.SERVER).status(SpanStatus.ERROR)
                .provenance(provenance(EvidenceFamily.TRACES)).build();
        var service = new IncidentAnalysisService(q -> TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(List.of(serverError)).build(), (a,e) -> topology);
        assertThat(service.analyze("test","local",FROM,TO,null).origin().component()).isEqualTo("api");
    }

    @Test
    void controlledExperimentUsesDeclaredOriginAndReportsHeldContainment() {
        var experiment = new com.madlanga.blastradius.domain.experiment.FailureExperiment(
                "postgres-outage-local", "postgres",
                java.util.Set.of("document-service", "customer-service", "payment-service"),
                java.util.Set.of("postgres", "document-service", "customer-service", "payment-service"));
        TelemetryProvider provider = q -> bundle(TelemetryCoverage.allAvailable());
        var topologyProvider = (com.madlanga.blastradius.ports.DependencyTopologyProvider) (app, env) ->
                new com.madlanga.blastradius.adapters.topology.TraceDiscoveredTopologyProvider(null)
                        .discover(app, env, bundle(TelemetryCoverage.allAvailable()).getSpans());
        var service = new IncidentAnalysisService(provider, topologyProvider,
                id -> "postgres-outage-local".equals(id) ? java.util.Optional.of(experiment) : java.util.Optional.empty());

        var result = service.analyze("document-platform", "local", FROM, TO, null, "postgres-outage-local");

        assertThat(result.origin().component()).isEqualTo("postgres");
        assertThat(result.experimentAssessment()).isNotNull();
        assertThat(result.experimentAssessment().containment())
                .isEqualTo(com.madlanga.blastradius.domain.experiment.ContainmentStatus.HELD);
        assertThat(result.experimentAssessment().expectedButUnobserved()).isEmpty();
        assertThat(result.experimentAssessment().unexpectedImpact()).isEmpty();
    }

    @Test
    void controlledExperimentIsInconclusiveWhenTelemetryCoverageIsPartial() {
        var experiment = new com.madlanga.blastradius.domain.experiment.FailureExperiment(
                "postgres-outage-local", "postgres",
                java.util.Set.of("document-service", "customer-service", "payment-service"),
                java.util.Set.of("postgres", "document-service", "customer-service", "payment-service"));
        var partial = TelemetryCoverage.builder().logs(CoverageStatus.UNAVAILABLE)
                .metrics(CoverageStatus.AVAILABLE).traces(CoverageStatus.AVAILABLE)
                .health(CoverageStatus.PARTIAL).build();
        TelemetryProvider provider = q -> bundle(partial);
        var topologyProvider = (com.madlanga.blastradius.ports.DependencyTopologyProvider) (app, env) ->
                new com.madlanga.blastradius.adapters.topology.TraceDiscoveredTopologyProvider(null)
                        .discover(app, env, bundle(partial).getSpans());
        var service = new IncidentAnalysisService(provider, topologyProvider, id -> java.util.Optional.of(experiment));

        var result = service.analyze("document-platform", "local", FROM, TO, null, "postgres-outage-local");

        assertThat(result.experimentAssessment().containment())
                .isEqualTo(com.madlanga.blastradius.domain.experiment.ContainmentStatus.INCONCLUSIVE);
    }

    @Test
    void rejectsUnknownControlledExperiment() {
        var service = new IncidentAnalysisService(q -> bundle(TelemetryCoverage.allAvailable()),
                (app, env) -> new com.madlanga.blastradius.adapters.topology.TraceDiscoveredTopologyProvider(null)
                        .discover(app, env, bundle(TelemetryCoverage.allAvailable()).getSpans()),
                id -> java.util.Optional.empty());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                service.analyze("document-platform", "local", FROM, TO, null, "missing-experiment")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown failure experiment");
    }

    private IncidentAnalysisService service(TelemetryProvider provider) {
        return new IncidentAnalysisService(provider, (app, env) -> {
            var spans = new java.util.ArrayList<>(provider.getTelemetry(TelemetryQuery.builder()
                    .applicationId(app).environment(env).from(FROM.minusSeconds(3600)).to(FROM).build()).getSpans());
            spans.addAll(provider.getTelemetry(TelemetryQuery.builder()
                    .applicationId(app).environment(env).from(FROM).to(TO).build()).getSpans());
            return new com.madlanga.blastradius.adapters.topology.TraceDiscoveredTopologyProvider(null)
                    .discover(app, env, spans);
        });
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
                .status(status).kind(peer == null ? SpanKind.INTERNAL : SpanKind.CLIENT).peerService(peer).attributes(attrs)
                .provenance(provenance(EvidenceFamily.TRACES)).build();
    }
    private HealthEvidence health(String service,HealthState state,Instant at){
        return HealthEvidence.builder().id("h-"+service).timestamp(at).service(service).environment("local")
                .endpoint("/actuator/health/readiness").state(state).provenance(provenance(EvidenceFamily.HEALTH)).build();
    }


    private MetricEvidence metricNamed(String id,String service,String name,double value,Instant at,Map<String,String> dimensions){
        return MetricEvidence.builder().id(id).timestamp(at).service(service).environment("local")
                .name(name).value(value).unit("requests").dimensions(dimensions)
                .provenance(provenance(EvidenceFamily.METRICS)).build();
    }

    private LogEvidence log(String service,String level,Instant at){
        return LogEvidence.builder().id("l-"+service).timestamp(at).service(service).environment("local")
                .level(level).message("sanitized test error").provenance(provenance(EvidenceFamily.LOGS)).build();
    }
    private MetricEvidence metric(String id,String service,double value,Instant at){
        return MetricEvidence.builder().id(id).timestamp(at).service(service).environment("local")
                .name("http.server.requests.seconds.count").value(value).unit("requests")
                .dimensions(Map.of("status","500")).provenance(provenance(EvidenceFamily.METRICS)).build();
    }

    private EvidenceProvenance provenance(EvidenceFamily family){
        return EvidenceProvenance.of(family,"test",TO,"fixture");
    }
}
