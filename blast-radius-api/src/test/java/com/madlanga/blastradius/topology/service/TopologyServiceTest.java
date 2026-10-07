package com.madlanga.blastradius.topology.service;

import com.madlanga.blastradius.topology.repository.FileTopologyRepository;
import com.madlanga.blastradius.telemetry.model.*;
import com.madlanga.blastradius.topology.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TopologyServiceTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");
    private TopologyService provider(Instant now) {
        return new TopologyService(new FileTopologyRepository(directory), q -> List.of(), Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofDays(7), Duration.ofMinutes(5));
    }
    private SpanEvidence span(String id, String service, String peer, Instant at) {
        return SpanEvidence.builder().id(id).traceId("trace").spanId(id).service(service).environment("test")
                .peerService(peer).startTime(at).status(SpanStatus.OK).kind(SpanKind.CLIENT)
                .attributes(peer == null ? Map.of() : Map.of("db.system.name","mongodb"))
                .provenance(EvidenceProvenance.of(EvidenceFamily.TRACES,"runtime-provider",NOW,"trace/ref")).build();
    }
    @Test void automaticSourceRefreshLearnsWithoutManualTopologyAndSurvivesRestart() {
        var source = List.of(span("1","api","catalog-db",NOW.minusSeconds(10)));
        var first = new TopologyService(new FileTopologyRepository(directory), q -> source,
                Clock.fixed(NOW,ZoneOffset.UTC),Duration.ofDays(7),Duration.ofMinutes(5));
        first.getTopology("catalog","test");
        var restored = provider(NOW.plusSeconds(3600)).getTopology("catalog","test");
        assertThat(restored.getEdges()).containsExactly(new DependencyEdge("api","catalog-db"));
        assertThat(restored.getNode("catalog-db").getType()).isEqualTo(ComponentType.DATABASE);
        assertThat(restored.getNode("catalog-db").getTechnology()).isEqualTo("MONGODB");
        var snapshot = new FileTopologyRepository(directory).load("catalog","test");
        assertThat(snapshot.edges().getFirst().observation().firstSeen()).isEqualTo(NOW.minusSeconds(10));
        assertThat(snapshot.edges().getFirst().observation().lastSeen()).isEqualTo(NOW.minusSeconds(10));
        assertThat(snapshot.edges().getFirst().observation().source()).isEqualTo("runtime-provider");
    }
    @Test void mergesNewDiscoveriesDuplicatesAndCyclesWithoutLosingEarlierKnowledge() {
        var p = provider(NOW);
        var one = span("1","api","catalog-db",NOW.minusSeconds(30));
        p.learn("catalog","test",List.of(one,one));
        p.learn("catalog","test",List.of(span("2","api","archive-db",NOW.minusSeconds(10)),
                span("3","archive-db","api",NOW.minusSeconds(5)),one));
        var topology = p.getTopology("catalog","test");
        assertThat(topology.getEdges()).hasSize(3).doesNotHaveDuplicates();
        assertThat(topology.getNodes()).hasSize(3);
        assertThat(topology.getNode("catalog-db").getTechnology()).isEqualTo("MONGODB");
        assertThat(new com.madlanga.blastradius.topology.service.BlastRadiusGraphService().calculate(topology,"catalog-db").getImpacts())
                .hasSize(2);
    }
    @Test void expiresEdgesEvenWhenOwnerIsAliveAndRereadingOldSpansDoesNotRenewThem() {
        var old = span("old","api","catalog-db",NOW.minus(Duration.ofDays(6)));
        provider(NOW).learn("catalog","test",List.of(old));
        var later = provider(NOW.plus(Duration.ofDays(2)));
        var topology = later.learn("catalog","test",List.of(old,span("alive","api",null,NOW.plus(Duration.ofDays(2)).minusSeconds(1))));
        assertThat(topology.getEdges()).isEmpty();
        assertThat(topology.getNodesById()).containsOnlyKeys("api");
        assertThat(new FileTopologyRepository(directory).load("catalog","test").edges()).isEmpty();
    }
    @Test void observationTimesMergeMonotonicallyAndAreIsolatedByScope() {
        var p=provider(NOW);
        p.learn("catalog","test",List.of(span("new","api","catalog-db",NOW.minusSeconds(10))));
        p.learn("catalog","test",List.of(span("old","api","catalog-db",NOW.minusSeconds(50))));
        var observed = new FileTopologyRepository(directory).load("catalog","test").edges().getFirst().observation();
        assertThat(observed.firstSeen()).isEqualTo(NOW.minusSeconds(50));
        assertThat(observed.lastSeen()).isEqualTo(NOW.minusSeconds(10));
        assertThat(p.getTopology("other","test").getNodes()).isEmpty();
        assertThat(p.getTopology("catalog","production").getNodes()).isEmpty();
    }
    @Test void rejectsFutureAndWrongEnvironmentObservations() {
        assertThat(provider(NOW).learn("catalog","test",List.of(span("future","api","db",NOW.plusSeconds(1)))).getNodes()).isEmpty();
        assertThat(provider(NOW).learn("catalog","production",List.of(span("wrong-env","api","db",NOW.minusSeconds(1)))).getNodes()).isEmpty();
    }
    @Test void corruptStoreIsAnExplicitFailureRatherThanAnEmptyGraph() throws Exception {
        provider(NOW).learn("catalog","test",List.of(span("one","api","db",NOW.minusSeconds(1))));
        try(var files=Files.list(directory)) { Files.writeString(files.findFirst().orElseThrow(), "invalid"); }
        assertThatThrownBy(() -> provider(NOW).getTopology("catalog","test"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot read retained topology");
    }
    @Test void identicalSpanIdsInDifferentTracesCannotCreateCrossTraceDependencies() {
        var parent = span("shared","unrelated",null,NOW.minusSeconds(5));
        var child = SpanEvidence.builder().id("child").traceId("different-trace").spanId("child")
                .parentSpanId("shared").service("api").environment("test").startTime(NOW.minusSeconds(4))
                .provenance(EvidenceProvenance.of(EvidenceFamily.TRACES,"test",NOW,"ref")).build();
        assertThat(provider(NOW).learn("catalog","test",List.of(parent,child)).getEdges()).isEmpty();
    }
}
