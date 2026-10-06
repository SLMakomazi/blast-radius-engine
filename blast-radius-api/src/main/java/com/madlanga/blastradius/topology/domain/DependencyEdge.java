package com.madlanga.blastradius.topology.domain;

import java.util.Objects;

/**
 * Directed dependency. For A -> B, A depends on B.
 */
public final class DependencyEdge {
    private final String dependentId;
    private final String dependencyId;

    public DependencyEdge(String dependentId, String dependencyId) {
        this.dependentId = requireText(dependentId, "dependentId");
        this.dependencyId = requireText(dependencyId, "dependencyId");
        if (this.dependentId.equals(this.dependencyId)) {
            throw new IllegalArgumentException("self-dependencies are not allowed");
        }
    }

    public String getDependentId() { return dependentId; }
    public String getDependencyId() { return dependencyId; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DependencyEdge edge)) return false;
        return dependentId.equals(edge.dependentId) && dependencyId.equals(edge.dependencyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dependentId, dependencyId);
    }
}
