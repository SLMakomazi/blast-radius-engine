package com.madlanga.blastradius.service;

import com.madlanga.blastradius.domain.incident.*;
import com.madlanga.blastradius.domain.topology.ComponentNode;
import com.madlanga.blastradius.domain.topology.DependencyTopology;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IncidentSeverityCalculator {
    public IncidentSeverity calculate(DependencyTopology topology, OriginAssessment origin, List<ComponentImpact> impacts) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        ComponentNode originNode = topology.getNode(origin.component());
        String criticality = originNode == null ? null : originNode.getMetadata().get("criticality");
        if (criticality != null && ("HIGH".equalsIgnoreCase(criticality) || "CRITICAL".equalsIgnoreCase(criticality))) {
            score += 20;
            reasons.add("origin component is marked " + criticality.toUpperCase(Locale.ROOT) + " criticality");
        }

        long observed = impacts.stream().filter(i -> i.state() == ObservedState.OBSERVED).count();
        if (observed > 0) {
            int points = (int) Math.min(30, observed * 10);
            score += points;
            reasons.add(observed + " dependent component(s) have observed failure evidence");
        }

        int depth = impacts.stream().filter(i -> i.distance() != null).mapToInt(ComponentImpact::distance).max().orElse(0);
        if (depth >= 3) {
            score += 20;
            reasons.add("failure propagated at least 3 dependency hops");
        } else if (depth >= 2) {
            score += 10;
            reasons.add("failure propagated at least 2 dependency hops");
        }

        long unexpected = impacts.stream().filter(i -> i.state() == ObservedState.UNEXPECTED).count();
        if (unexpected > 0) {
            int points = (int) Math.min(40, unexpected * 20);
            score += points;
            reasons.add(unexpected + " observed component(s) are outside the theoretical radius");
        }

        boolean availabilityFailure = impacts.stream().flatMap(i -> i.evidence().stream())
                .anyMatch(e -> "HEALTH".equals(e.family()));
        if (availabilityFailure) {
            score += 15;
            reasons.add("availability/health degradation is observed");
        }

        score = Math.min(100, score);
        SeverityLevel level = score >= 70 ? SeverityLevel.CRITICAL
                : score >= 40 ? SeverityLevel.HIGH
                : score >= 20 ? SeverityLevel.MEDIUM : SeverityLevel.LOW;
        if (reasons.isEmpty()) reasons.add("no configured severity escalation factor was observed");
        return new IncidentSeverity(level, score, reasons);
    }
}
