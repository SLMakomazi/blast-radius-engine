package com.madlanga.blastradius.incident.service;

import com.madlanga.blastradius.incident.model.ComponentImpact;
import com.madlanga.blastradius.incident.model.EvidenceSignal;
import com.madlanga.blastradius.incident.model.IncidentSeverity;
import com.madlanga.blastradius.incident.model.OriginAssessment;
import com.madlanga.blastradius.topology.domain.ComponentNode;
import com.madlanga.blastradius.topology.domain.DependencyTopology;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IncidentSeverityService {
    public IncidentSeverity calculate(DependencyTopology topology, OriginAssessment origin, List<ComponentImpact> impacts) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        ComponentNode originNode = topology.getNode(origin.component());
        String criticality = originNode == null ? null : originNode.getMetadata().get("criticality");
        if (criticality != null && ("HIGH".equalsIgnoreCase(criticality) || "CRITICAL".equalsIgnoreCase(criticality))) {
            score += 20;
            reasons.add("origin component is marked " + criticality.toUpperCase(Locale.ROOT) + " criticality");
        }

        // An incident with direct evidence on its origin is a real failure, not merely
        // theoretical reachability. Give it a baseline before blast-radius escalation.
        if (!origin.evidence().isEmpty()) {
            score += 20;
            reasons.add("failure evidence is confirmed on the origin component");
        }

        long observed = impacts.stream().filter(i -> i.state() == ComponentImpact.State.OBSERVED).count();
        if (observed > 0) {
            int points = (int) Math.min(30, observed * 10);
            score += points;
            reasons.add(observed + " dependent component(s) have observed failure evidence");
        }

        int observedDepth = impacts.stream()
                .filter(i -> i.state() == ComponentImpact.State.OBSERVED)
                .filter(i -> i.distance() != null)
                .mapToInt(ComponentImpact::distance)
                .max()
                .orElse(0);
        if (observedDepth >= 3) {
            score += 20;
            reasons.add("failure propagated at least 3 dependency hops");
        } else if (observedDepth >= 2) {
            score += 10;
            reasons.add("failure propagated at least 2 dependency hops");
        }

        long unexpected = impacts.stream().filter(i -> i.state() == ComponentImpact.State.UNEXPECTED).count();
        if (unexpected > 0) {
            int points = (int) Math.min(40, unexpected * 20);
            score += points;
            reasons.add(unexpected + " observed component(s) are outside the theoretical radius");
        }

        // Availability evidence can live on the ORIGIN itself (for example an
        // unreachable Actuator endpoint). The previous implementation inspected only
        // impact evidence and therefore missed exactly that case.
        boolean availabilityFailure = origin.evidence().stream()
                .anyMatch(e -> "HEALTH".equals(e.family()))
                || impacts.stream().flatMap(i -> i.evidence().stream())
                        .anyMatch(e -> "HEALTH".equals(e.family()));
        if (availabilityFailure) {
            score += 20;
            reasons.add("availability/health degradation is observed");
        }

        // A directly observed dependent failure is user-facing blast radius. This
        // prevents a total middle-tier outage with upstream 5xx traffic from being
        // labelled LOW merely because propagation depth is one hop.
        if (observed > 0) {
            score += 10;
            reasons.add("failure has propagated beyond the origin component");
        }

        score = Math.min(100, score);
        IncidentSeverity.Level level = score >= 70 ? IncidentSeverity.Level.CRITICAL
                : score >= 40 ? IncidentSeverity.Level.HIGH
                : score >= 20 ? IncidentSeverity.Level.MEDIUM : IncidentSeverity.Level.LOW;
        if (reasons.isEmpty()) reasons.add("no configured severity escalation factor was observed");
        return new IncidentSeverity(level, score, reasons);
    }
}
