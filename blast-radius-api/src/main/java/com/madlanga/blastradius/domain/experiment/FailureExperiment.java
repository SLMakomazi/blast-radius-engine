package com.madlanga.blastradius.domain.experiment;

import java.util.Set;

public record FailureExperiment(
        String id,
        String originComponent,
        Set<String> expectedAffectedComponents,
        Set<String> containmentBoundary) {
    public FailureExperiment {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("experiment id must not be blank");
        if (originComponent == null || originComponent.isBlank()) throw new IllegalArgumentException("originComponent must not be blank");
        expectedAffectedComponents = expectedAffectedComponents == null ? Set.of() : Set.copyOf(expectedAffectedComponents);
        containmentBoundary = containmentBoundary == null ? Set.of() : Set.copyOf(containmentBoundary);
    }
}
