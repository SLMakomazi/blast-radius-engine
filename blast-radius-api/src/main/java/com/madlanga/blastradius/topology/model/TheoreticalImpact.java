package com.madlanga.blastradius.topology.model;

import java.util.List;
import java.util.Objects;

public final class TheoreticalImpact {
    private final ComponentNode component;
    private final int distance;
    private final ImpactClassification classification;
    private final List<String> path;

    public TheoreticalImpact(ComponentNode component, int distance, List<String> path) {
        this.component = Objects.requireNonNull(component, "component must not be null");
        if (distance < 1) throw new IllegalArgumentException("distance must be at least 1");
        this.distance = distance;
        this.classification = distance == 1 ? ImpactClassification.DIRECT : ImpactClassification.INDIRECT;
        this.path = List.copyOf(Objects.requireNonNull(path, "path must not be null"));
        if (this.path.size() != distance + 1) {
            throw new IllegalArgumentException("path length must equal distance + 1");
        }
    }

    public ComponentNode getComponent() { return component; }
    public int getDistance() { return distance; }
    public ImpactClassification getClassification() { return classification; }
    public List<String> getPath() { return path; }
}
