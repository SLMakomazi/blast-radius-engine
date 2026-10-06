package com.madlanga.blastradius.telemetry.infrastructure.tempo;

import com.madlanga.blastradius.topology.infrastructure.persistence.FileTopologyStore;
import com.madlanga.blastradius.topology.infrastructure.RetainedTopologyProvider;
import com.madlanga.blastradius.telemetry.domain.*;
import com.madlanga.blastradius.domain.incident.*;
import com.madlanga.blastradius.topology.domain.*;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;
import com.madlanga.blastradius.service.IncidentAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CapturedTempoRegressionTest {
    @TempDir Path directory;
    static final Instant FROM = Instant.parse("2026-10-02T15:47:25Z");
    static final Instant TO = Instant.parse("2026-10-02T15:52:25Z");

    private TempoTraceAdapter.TraceAdapterResult load(String name, Instant from, Instant to) throws Exception {
        String json;
        try (var input = getClass().getResourceAsStream("/tempo/" + name + ".json")) {
            json = new String(Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://tempo:3200/api/traces/captured"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        var adapter = new TempoTraceAdapter(new TempoProperties(), new TelemetrySanitizer(), builder);
        var result = adapter.fetchSpans(TelemetryQuery.builder().applicationId("document-platform")
                .environment("local").from(from).to(to).traceId("captured").build());
        server.verify();
        return result;
    }
    private List<SpanEvidence> healthy() throws Exception {
        return load("healthy-database", FROM.minus(Duration.ofHours(3)), FROM).getSpans();
    }
    private RetainedTopologyProvider provider() {
        return new RetainedTopologyProvider(new FileTopologyStore(directory), q -> List.of(),
                Clock.fixed(TO, ZoneOffset.UTC), Duration.ofDays(7), Duration.ofMinutes(5));
    }

    @Test void concreteDatabaseAndTechnologyOnlyPoolProduceExactlyOneDependency() throws Exception {
        List<SpanEvidence> spans = healthy();
        assertThat(spans).hasSize(8);
        assertThat(spans).filteredOn(s -> s.getKind() == SpanKind.INTERNAL)
                .allSatisfy(s -> { assertThat(s.getPeerService()).isNull();
                    assertThat(s.getAttributes()).containsEntry("db.system", "postgresql"); });
        DependencyTopology topology = provider().learn("document-platform", "local", spans);
        assertThat(topology.getNodesById()).containsOnlyKeys("payment-service", "customer-service", "document-service", "postgres");
        assertThat(topology.getNode("postgres").getType()).isEqualTo(ComponentType.DATABASE);
        assertThat(topology.getNode("postgres").getTechnology()).isEqualTo("POSTGRESQL");
        assertThat(topology.getEdges()).hasSize(3).contains(new DependencyEdge("document-service", "postgres"));
    }

    @Test void restartAndRawTraceLossStillCorrelateRealPeerlessOutageWithoutHistoricalEvidence() throws Exception {
        var originalHealthy = healthy();
        provider().learn("document-platform", "local", originalHealthy);
        var failed = load("failed-connection", FROM, TO).getSpans();
        assertThat(failed).hasSize(6);
        assertThat(failed).filteredOn(s -> s.getKind() == SpanKind.INTERNAL).singleElement().satisfies(s -> {
            assertThat(s.isError()).isTrue(); assertThat(s.getAttributes()).isEmpty();
            assertThat(s.getPeerService()).isNull();
        });
        // A new provider reads the persisted snapshot; the runtime source has lost every old trace.
        var result = new IncidentAnalysisService(q -> TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(failed).build(), provider()).analyze("document-platform", "local", FROM, TO, null);
        assertThat(result.origin().component()).isEqualTo("postgres");
        assertThat(result.impacts()).extracting(ComponentImpact::distance).containsExactly(0,1,2,3);
        assertThat(result.impacts().getLast().path()).containsExactly("postgres","document-service","customer-service","payment-service");
        assertThat(result.impacts()).extracting(ComponentImpact::state).containsExactly(ObservedState.ORIGIN,
                ObservedState.OBSERVED,ObservedState.OBSERVED,ObservedState.OBSERVED);
        assertThat(result.timeline()).allSatisfy(s -> assertThat(s.timestamp()).isBetween(FROM, TO));
        assertThat(result.timeline()).noneMatch(s -> originalHealthy.stream().anyMatch(h -> h.getId().equals(s.evidenceId())));
    }

    @Test void oldFailureInsideReturnedTraceCannotBecomeCurrentIncidentEvidence() throws Exception {
        var result = load("failed-connection", TO, TO.plusSeconds(300));
        assertThat(result.getSpans()).isEmpty();
        assertThat(result.getCoverage()).isEqualTo(CoverageStatus.UNAVAILABLE);
        provider().learn("document-platform", "local", healthy());
        var historicErrors = load("failed-connection", FROM, TO).getSpans();
        var service = new IncidentAnalysisService(q -> TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(historicErrors).build(), provider());
        assertThatThrownBy(() -> service.analyze("document-platform","local",TO,TO.plusSeconds(300),null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No failure evidence");
    }

    @Test void retainedHealthyRelationshipsAloneDoNotCreateAnIncident() throws Exception {
        provider().learn("document-platform", "local", healthy());
        var service = new IncidentAnalysisService(q -> TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable()).build(), provider());
        assertThatThrownBy(() -> service.analyze("document-platform","local",FROM,TO,null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No failure evidence");
    }

    @Test void twoRealDependenciesDoNotGuessAnOriginFromPeerlessError() throws Exception {
        var provider = provider();
        provider.learn("document-platform", "local", healthy());
        provider.learn("document-platform", "local", List.of(SpanEvidence.builder().id("alternate")
                .traceId("other").spanId("other").service("document-service").environment("local")
                .peerService("archive-store").attributes(Map.of("db.system.name","oracle"))
                .startTime(FROM.minusSeconds(10)).status(SpanStatus.OK).kind(SpanKind.CLIENT)
                .provenance(EvidenceProvenance.of(EvidenceFamily.TRACES,"test",FROM,"alternate-db")).build()));
        var failed = load("failed-connection", FROM, TO).getSpans();
        var result = new IncidentAnalysisService(q -> TelemetryBundle.builder().coverage(TelemetryCoverage.allAvailable())
                .spans(failed).build(), provider).analyze("document-platform","local",FROM,TO,null);
        assertThat(result.origin().component()).isEqualTo("document-service");
        assertThat(result.timeline()).noneMatch(s -> Set.of("postgres","archive-store").contains(s.component()));
    }
}
