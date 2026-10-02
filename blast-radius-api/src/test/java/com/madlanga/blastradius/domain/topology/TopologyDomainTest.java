package com.madlanga.blastradius.domain.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TopologyDomainTest {

    @Test
    void returnedCollectionsAreImmutable() {
        ComponentNode node = ComponentNode.builder()
                .id("svc").name("Service").type(ComponentType.SERVICE)
                .metadata(Map.of("owner", "team-a")).build();
        DependencyTopology topology = DependencyTopology.builder()
                .applicationId("app").environment("local")
                .nodes(List.of(node)).edges(List.of()).build();

        assertThatThrownBy(() -> topology.getNodes().add(node))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> node.getMetadata().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsSelfDependency() {
        assertThatThrownBy(() -> new DependencyEdge("svc", "svc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("self-dependencies");
    }

    @Test
    void impactValidatesPathLength() {
        ComponentNode node = ComponentNode.builder()
                .id("svc").name("Service").type(ComponentType.SERVICE).build();

        assertThatThrownBy(() -> new TheoreticalImpact(node, 2, List.of("origin", "svc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path length");
    }
}
