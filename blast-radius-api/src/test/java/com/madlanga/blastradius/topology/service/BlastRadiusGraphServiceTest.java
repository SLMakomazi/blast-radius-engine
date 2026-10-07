package com.madlanga.blastradius.topology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.madlanga.blastradius.topology.model.ComponentNode;
import com.madlanga.blastradius.topology.model.ComponentType;
import com.madlanga.blastradius.topology.model.DependencyEdge;
import com.madlanga.blastradius.topology.model.DependencyTopology;
import com.madlanga.blastradius.topology.model.BlastRadiusResult;
import com.madlanga.blastradius.topology.model.TheoreticalImpact.Classification;
import com.madlanga.blastradius.topology.model.TheoreticalImpact;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlastRadiusGraphServiceTest {

    private final BlastRadiusGraphService engine = new BlastRadiusGraphService();

    @Test
    void reverseTraversesCanonicalChainWithMinimumDistanceAndPath() {
        DependencyTopology topology = topology(
                List.of(node("payment"), node("customer"), node("document"), database("postgres")),
                List.of(edge("payment", "customer"), edge("customer", "document"), edge("document", "postgres")));

        BlastRadiusResult result = engine.calculate(topology, "postgres");

        assertThat(result.getOrigin().getId()).isEqualTo("postgres");
        assertThat(result.getImpacts()).extracting(i -> i.getComponent().getId())
                .containsExactly("document", "customer", "payment");
        assertImpact(result, "document", 1, TheoreticalImpact.Classification.DIRECT, "postgres", "document");
        assertImpact(result, "customer", 2, TheoreticalImpact.Classification.INDIRECT, "postgres", "document", "customer");
        assertImpact(result, "payment", 3, TheoreticalImpact.Classification.INDIRECT, "postgres", "document", "customer", "payment");
    }

    @Test
    void fanOutReturnsAllDependentsInDeterministicOrder() {
        DependencyTopology topology = topology(
                List.of(node("alpha"), node("beta"), database("db")),
                List.of(edge("beta", "db"), edge("alpha", "db")));

        BlastRadiusResult result = engine.calculate(topology, "db");

        assertThat(result.getImpacts()).extracting(i -> i.getComponent().getId())
                .containsExactly("alpha", "beta");
    }

    @Test
    void fanInKeepsShortestPathAndLexicographicallyStableTieBreak() {
        DependencyTopology topology = topology(
                List.of(node("api"), node("left"), node("right"), database("db")),
                List.of(
                        edge("api", "right"),
                        edge("api", "left"),
                        edge("right", "db"),
                        edge("left", "db")));

        BlastRadiusResult result = engine.calculate(topology, "db");

        assertImpact(result, "api", 2, TheoreticalImpact.Classification.INDIRECT, "db", "left", "api");
    }

    @Test
    void cyclesTerminateAndDoNotIncludeOriginAsAffected() {
        DependencyTopology topology = topology(
                List.of(node("a"), node("b"), node("c")),
                List.of(edge("a", "b"), edge("b", "c"), edge("c", "a")));

        BlastRadiusResult result = engine.calculate(topology, "c");

        assertThat(result.getImpacts()).hasSize(2);
        assertImpact(result, "b", 1, TheoreticalImpact.Classification.DIRECT, "c", "b");
        assertImpact(result, "a", 2, TheoreticalImpact.Classification.INDIRECT, "c", "b", "a");
        assertThat(result.getImpacts()).noneMatch(i -> i.getComponent().getId().equals("c"));
    }

    @Test
    void disconnectedComponentsAreExcluded() {
        DependencyTopology topology = topology(
                List.of(node("api"), database("db"), node("unrelated")),
                List.of(edge("api", "db")));

        BlastRadiusResult result = engine.calculate(topology, "db");

        assertThat(result.getImpacts()).extracting(i -> i.getComponent().getId())
                .containsExactly("api");
    }

    @Test
    void duplicateEdgesDoNotDuplicateImpact() {
        DependencyEdge duplicate = edge("api", "db");
        DependencyTopology topology = topology(
                List.of(node("api"), database("db")),
                List.of(duplicate, duplicate, edge("api", "db")));

        assertThat(topology.getEdges()).hasSize(1);
        assertThat(engine.calculate(topology, "db").getImpacts()).hasSize(1);
    }

    @Test
    void originWithNoDependentsReturnsEmptyRadius() {
        DependencyTopology topology = topology(
                List.of(node("api"), database("db")),
                List.of(edge("api", "db")));

        assertThat(engine.calculate(topology, "api").getImpacts()).isEmpty();
    }

    @Test
    void rejectsUnknownOrigin() {
        DependencyTopology topology = topology(List.of(node("api")), List.of());

        assertThatThrownBy(() -> engine.calculate(topology, "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origin is not present");
    }

    @Test
    void topologyRejectsUnknownEdgeEndpoints() {
        assertThatThrownBy(() -> topology(
                List.of(node("api")),
                List.of(edge("api", "missing"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown dependency");
    }

    @Test
    void topologyRejectsDuplicateComponentIds() {
        assertThatThrownBy(() -> topology(
                List.of(node("same"), node("same")),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate component id");
    }

    @Test
    void componentTypeAndTechnologyRemainIndependent() {
        ComponentNode node = ComponentNode.builder()
                .id("orders-db")
                .name("Orders database")
                .type(ComponentType.DATABASE)
                .technology("MONGODB")
                .build();

        assertThat(node.getType()).isEqualTo(ComponentType.DATABASE);
        assertThat(node.getTechnology()).isEqualTo("MONGODB");
    }

    private void assertImpact(BlastRadiusResult result, String id, int distance,
                              TheoreticalImpact.Classification classification, String... path) {
        TheoreticalImpact impact = result.getImpacts().stream()
                .filter(candidate -> candidate.getComponent().getId().equals(id))
                .findFirst()
                .orElseThrow();
        assertThat(impact.getDistance()).isEqualTo(distance);
        assertThat(impact.getClassification()).isEqualTo(classification);
        assertThat(impact.getPath()).containsExactly(path);
    }

    private DependencyTopology topology(List<ComponentNode> nodes, List<DependencyEdge> edges) {
        return DependencyTopology.builder()
                .applicationId("document-platform")
                .environment("local")
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    private ComponentNode node(String id) {
        return ComponentNode.builder().id(id).name(id).type(ComponentType.SERVICE).technology("TEST").build();
    }

    private ComponentNode database(String id) {
        return ComponentNode.builder().id(id).name(id).type(ComponentType.DATABASE).technology("POSTGRESQL").build();
    }

    private DependencyEdge edge(String dependent, String dependency) {
        return new DependencyEdge(dependent, dependency);
    }
}
