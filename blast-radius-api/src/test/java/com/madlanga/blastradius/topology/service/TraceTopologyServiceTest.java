package com.madlanga.blastradius.topology.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.madlanga.blastradius.telemetry.model.EvidenceFamily;
import com.madlanga.blastradius.telemetry.model.EvidenceProvenance;
import com.madlanga.blastradius.telemetry.model.SpanEvidence;
import com.madlanga.blastradius.telemetry.model.SpanStatus;
import com.madlanga.blastradius.topology.model.ComponentType;
import com.madlanga.blastradius.topology.model.DependencyTopology;
import com.madlanga.blastradius.topology.service.BlastRadiusGraphService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraceTopologyServiceTest {

    @Test
    void discoversCanonicalTopologyFromParentChildSpansAndDatabasePeer() {
        TraceTopologyService provider = new TraceTopologyService(
                query -> null, Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(15));

        DependencyTopology topology = provider.discover("document-platform", "local", List.of(
                span("p-server", null, "checkout-api", null, Map.of()),
                span("p-client", "p-server", "checkout-api", "account-api", Map.of()),
                span("c-server", "p-client", "account-api", null, Map.of()),
                span("c-client", "c-server", "account-api", "storage-api", Map.of()),
                span("d-server", "c-client", "storage-api", null, Map.of()),
                span("jdbc", "d-server", "storage-api", "postgres", Map.of("db.system.name", "postgresql"))));

        assertThat(topology.getNodes()).extracting(n -> n.getId())
                .containsExactly("account-api", "checkout-api", "postgres", "storage-api");
        assertThat(topology.getEdges()).extracting(e -> e.getDependentId() + "->" + e.getDependencyId())
                .containsExactly(
                        "account-api->storage-api",
                        "checkout-api->account-api",
                        "storage-api->postgres");
        assertThat(topology.getNode("postgres").getType()).isEqualTo(ComponentType.DATABASE);
        assertThat(topology.getNode("postgres").getTechnology()).isEqualTo("POSTGRESQL");

        assertThat(new BlastRadiusGraphService().calculate(topology, "postgres").getImpacts())
                .extracting(i -> i.getComponent().getId())
                .containsExactly("storage-api", "account-api", "checkout-api");
    }

    @Test
    void deduplicatesDependenciesObservedAcrossRepeatedTraces() {
        TraceTopologyService provider = new TraceTopologyService(
                query -> null, Clock.systemUTC(), Duration.ofMinutes(15));

        DependencyTopology topology = provider.discover("app", "local", List.of(
                span("one", null, "checkout-api", "account-api", Map.of()),
                span("two", null, "checkout-api", "account-api", Map.of())));

        assertThat(topology.getEdges()).hasSize(1);
    }

    private SpanEvidence span(String id, String parentId, String service, String peer,
                              Map<String, String> attributes) {
        return SpanEvidence.builder()
                .id("e-" + id)
                .traceId("0123456789abcdef0123456789abcdef")
                .spanId(id)
                .parentSpanId(parentId)
                .service(service)
                .environment("local")
                .operation("test")
                .startTime(Instant.parse("2026-10-02T11:59:00Z"))
                .durationMs(10)
                .status(SpanStatus.OK)
                .peerService(peer)
                .attributes(attributes)
                .provenance(EvidenceProvenance.of(EvidenceFamily.TRACES, "test", Instant.now(), "fixture"))
                .build();
    }
}
