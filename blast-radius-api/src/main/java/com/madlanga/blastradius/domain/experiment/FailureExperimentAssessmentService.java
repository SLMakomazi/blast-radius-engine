package com.madlanga.blastradius.domain.experiment;

import com.madlanga.blastradius.domain.experiment.*;
import com.madlanga.blastradius.domain.incident.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FailureExperimentAssessmentService {
    public ExperimentAssessment assess(FailureExperiment experiment, List<ComponentImpact> impacts, boolean fullyCovered) {
        Set<String> observed = new LinkedHashSet<>();
        for (ComponentImpact impact : impacts) {
            if (impact.state() == ObservedState.ORIGIN || impact.state() == ObservedState.OBSERVED
                    || impact.state() == ObservedState.UNEXPECTED) observed.add(impact.component());
        }

        Set<String> expected = new LinkedHashSet<>(experiment.expectedAffectedComponents());
        expected.add(experiment.originComponent());

        Set<String> observedExpected = intersection(observed, expected);
        Set<String> missed = difference(expected, observed);
        Set<String> unexpected = difference(observed, expected);

        boolean escapedBoundary = !experiment.containmentBoundary().isEmpty()
                && observed.stream().anyMatch(component -> !experiment.containmentBoundary().contains(component));
        ContainmentStatus status = escapedBoundary ? ContainmentStatus.BREACHED
                : !fullyCovered ? ContainmentStatus.INCONCLUSIVE
                : ContainmentStatus.HELD;

        return new ExperimentAssessment(experiment.id(), status, expected, observedExpected, missed, unexpected);
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left); result.retainAll(right); return result;
    }
    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left); result.removeAll(right); return result;
    }
}
