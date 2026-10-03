package com.madlanga.blastradius.domain.experiment;

import java.util.Set;

public record ExperimentAssessment(
        String experimentId,
        ContainmentStatus containment,
        Set<String> expectedImpact,
        Set<String> observedExpectedImpact,
        Set<String> expectedButUnobserved,
        Set<String> unexpectedImpact) {
    public ExperimentAssessment {
        expectedImpact = Set.copyOf(expectedImpact);
        observedExpectedImpact = Set.copyOf(observedExpectedImpact);
        expectedButUnobserved = Set.copyOf(expectedButUnobserved);
        unexpectedImpact = Set.copyOf(unexpectedImpact);
    }
}
