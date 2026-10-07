package com.madlanga.blastradius.topology.model;

import java.util.List;
import java.util.Objects;

public final class GraphAnalysisResult {
    private final ComponentNode origin;
    private final List<TheoreticalImpact> impacts;

    public GraphAnalysisResult(ComponentNode origin, List<TheoreticalImpact> impacts) {
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
        this.impacts = List.copyOf(Objects.requireNonNull(impacts, "impacts must not be null"));
    }

    public ComponentNode getOrigin() { return origin; }
    public List<TheoreticalImpact> getImpacts() { return impacts; }
}
