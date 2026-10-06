package com.madlanga.blastradius.topology.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DependencyTopology {
    private final String applicationId;
    private final String environment;
    private final Map<String, ComponentNode> nodesById;
    private final List<DependencyEdge> edges;

    private DependencyTopology(Builder builder) {
        this.applicationId = requireText(builder.applicationId, "applicationId");
        this.environment = requireText(builder.environment, "environment");

        Map<String, ComponentNode> nodes = new LinkedHashMap<>();
        for (ComponentNode node : builder.nodes) {
            Objects.requireNonNull(node, "nodes must not contain null");
            if (nodes.putIfAbsent(node.getId(), node) != null) {
                throw new IllegalArgumentException("duplicate component id: " + node.getId());
            }
        }

        LinkedHashSet<DependencyEdge> uniqueEdges = new LinkedHashSet<>();
        for (DependencyEdge edge : builder.edges) {
            Objects.requireNonNull(edge, "edges must not contain null");
            if (!nodes.containsKey(edge.getDependentId())) {
                throw new IllegalArgumentException("edge references unknown dependent: " + edge.getDependentId());
            }
            if (!nodes.containsKey(edge.getDependencyId())) {
                throw new IllegalArgumentException("edge references unknown dependency: " + edge.getDependencyId());
            }
            uniqueEdges.add(edge);
        }

        this.nodesById = Collections.unmodifiableMap(nodes);
        this.edges = Collections.unmodifiableList(new ArrayList<>(uniqueEdges));
    }

    public String getApplicationId() { return applicationId; }
    public String getEnvironment() { return environment; }
    public Map<String, ComponentNode> getNodesById() { return nodesById; }
    public List<ComponentNode> getNodes() { return List.copyOf(nodesById.values()); }
    public List<DependencyEdge> getEdges() { return edges; }
    public ComponentNode getNode(String id) { return nodesById.get(id); }

    public static Builder builder() { return new Builder(); }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    public static final class Builder {
        private String applicationId;
        private String environment;
        private List<ComponentNode> nodes = new ArrayList<>();
        private List<DependencyEdge> edges = new ArrayList<>();

        private Builder() {}

        public Builder applicationId(String applicationId) { this.applicationId = applicationId; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder nodes(List<ComponentNode> nodes) {
            this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
            return this;
        }
        public Builder edges(List<DependencyEdge> edges) {
            this.edges = edges == null ? new ArrayList<>() : new ArrayList<>(edges);
            return this;
        }
        public DependencyTopology build() { return new DependencyTopology(this); }
    }
}
