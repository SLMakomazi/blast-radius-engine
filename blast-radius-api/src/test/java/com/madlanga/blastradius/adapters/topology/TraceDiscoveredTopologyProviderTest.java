package com.madlanga.blastradius.adapters.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.madlanga.blastradius.domain.evidence.EvidenceFamily;
import com.madlanga.blastradius.domain.evidence.EvidenceProvenance;
import com.madlanga.blastradius.domain.evidence.SpanEvidence;
import com.madlanga.blastradius.domain.evidence.SpanStatus;
import com.madlanga.blastradius.domain.topology.ComponentType;
import com.madlanga.blastradius.domain.topology.DependencyTopology;
import com.madlanga.blastradius.service.DeterministicGraphEngine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraceDiscoveredTopologyProviderTest {

    @Test
    void discoversCanonicalTopologyFromParentChildSpansAndDatabasePeer() {
        TraceDiscoveredTopologyProvider provider = new TraceDiscoveredTopologyProvider(
                query -> null, Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(15));

        DependencyTopology topology = provider.discover("document-platform", "local", List.of(
                span("p-server", null, "payment-service", null, Map.of()),
                span("p-client", "p-server", "payment-service", "customer-service", Map.of()),
                span("c-server", "p-client", "customer-service", null, Map.of()),
                span("c-client", "c-server", "customer-service", "document-service", Map.of()),
                span("d-server", "c-client", "document-service", null, Map.of()),
                span("jdbc", "d-server", "document-service", "postgres", Map.of("db.system.name", "postgresql"))));

        assertThat(topology.getNodes()).extracting(n -> n.getId())
                .containsExactly("customer-service", "document-service", "payment-service", "postgres");
        assertThat(topology.getEdges()).extracting(e -> e.getDependentId() + "->" + e.getDependencyId())
                .containsExactly(
                        "customer-service->document-service",
                        "document-service->postgres",
                        "payment-service->customer-service");
        assertThat(topology.getNode("postgres").getType()).isEqualTo(ComponentType.DATABASE);
        assertThat(topology.getNode("postgres").getTechnology()).isEqualTo("POSTGRESQL");

        assertThat(new DeterministicGraphEngine().calculate(topology, "postgres").getImpacts())
                .extracting(i -> i.getComponent().getId())
                .containsExactly("document-service", "customer-service", "payment-service");
    }

    @Test
    void deduplicatesDependenciesObservedAcrossRepeatedTraces() {
        TraceDiscoveredTopologyProvider provider = new TraceDiscoveredTopologyProvider(
                query -> null, Clock.systemUTC(), Duration.ofMinutes(15));

        DependencyTopology topology = provider.discover("app", "local", List.of(
                span("one", null, "payment-service", "customer-service", Map.of()),
                span("two", null, "payment-service", "customer-service", Map.of())));

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
