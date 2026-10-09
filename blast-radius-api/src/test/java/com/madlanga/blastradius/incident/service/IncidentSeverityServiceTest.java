package com.madlanga.blastradius.incident.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.madlanga.blastradius.incident.model.ComponentImpact;
import com.madlanga.blastradius.incident.model.EvidenceSignal;
import com.madlanga.blastradius.incident.model.IncidentSeverity;
import com.madlanga.blastradius.incident.model.OriginAssessment;
import com.madlanga.blastradius.topology.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncidentSeverityServiceTest {
    private final IncidentSeverityService calculator = new IncidentSeverityService();

    @Test
    void scoresThreeHopObservedAvailabilityFailureAsCritical() {
        var topology = topology(Map.of());
        var health = new EvidenceSignal(Instant.parse("2026-10-03T04:00:00Z"), "storage-api", "HEALTH", "health DOWN", "h1");
        var impacts = List.of(
                impact("postgres", ComponentImpact.State.ORIGIN, 0, List.of()),
                impact("storage-api", ComponentImpact.State.OBSERVED, 1, List.of(health)),
                impact("account-api", ComponentImpact.State.OBSERVED, 2, List.of()),
                impact("checkout-api", ComponentImpact.State.OBSERVED, 3, List.of()));
        var severity = calculator.calculate(topology, new OriginAssessment("postgres", OriginAssessment.Confidence.HIGH, 100, List.of()), impacts);
        assertThat(severity.score()).isEqualTo(80);
        assertThat(severity.level()).isEqualTo(IncidentSeverity.Level.CRITICAL);
    }

    @Test
    void theoreticalDepthDoesNotEscalateIncidentSeverityWithoutObservedPropagation() {
        var impacts = List.of(
                impact("postgres", ComponentImpact.State.ORIGIN, 0, List.of()),
                impact("storage-api", ComponentImpact.State.THEORETICAL_ONLY, 1, List.of()),
                impact("account-api", ComponentImpact.State.THEORETICAL_ONLY, 2, List.of()),
                impact("checkout-api", ComponentImpact.State.THEORETICAL_ONLY, 3, List.of()));
        var severity = calculator.calculate(topology(Map.of()),
                new OriginAssessment("postgres", OriginAssessment.Confidence.LOW, 0, List.of()), impacts);
        assertThat(severity.score()).isZero();
        assertThat(severity.level()).isEqualTo(IncidentSeverity.Level.LOW);
        assertThat(severity.reasons()).containsExactly("no configured severity escalation factor was observed");
    }

    @Test
    void unexpectedImpactAndCriticalOriginEscalateSeverity() {
        var impacts = List.of(
                impact("postgres", ComponentImpact.State.ORIGIN, 0, List.of()),
                impact("storage-api", ComponentImpact.State.OBSERVED, 1, List.of()),
                impact("external-service", ComponentImpact.State.UNEXPECTED, null, List.of()));
        var severity = calculator.calculate(topology(Map.of("criticality","HIGH")),
                new OriginAssessment("postgres", OriginAssessment.Confidence.HIGH, 100, List.of()), impacts);
        assertThat(severity.score()).isEqualTo(60);
        assertThat(severity.level()).isEqualTo(IncidentSeverity.Level.HIGH);
        assertThat(severity.reasons()).anyMatch(r -> r.contains("outside the theoretical radius"));
    }

    @Test
    void directOriginAvailabilityFailureIsAtLeastHigh() {
        var health = new EvidenceSignal(Instant.parse("2026-10-04T06:00:00Z"),
                "account-api", "HEALTH", "health UNREACHABLE", "h-customer");
        var severity = calculator.calculate(topology(Map.of()),
                new OriginAssessment("account-api", OriginAssessment.Confidence.HIGH, 100, List.of(health)),
                List.of(
                        impact("account-api", ComponentImpact.State.ORIGIN, 0, List.of(health)),
                        impact("checkout-api", ComponentImpact.State.OBSERVED, 1, List.of())));

        assertThat(severity.score()).isEqualTo(60);
        assertThat(severity.level()).isEqualTo(IncidentSeverity.Level.HIGH);
        assertThat(severity.reasons()).contains(
                "failure evidence is confirmed on the origin component",
                "availability/health degradation is observed",
                "failure has propagated beyond the origin component");
    }

    @Test
    void directOriginFailureWithoutAvailabilityEvidenceIsMedium() {
        var trace = new EvidenceSignal(Instant.parse("2026-10-04T06:00:00Z"),
                "account-api", "TRACE", "error span: request", "t-customer");
        var severity = calculator.calculate(topology(Map.of()),
                new OriginAssessment("account-api", OriginAssessment.Confidence.MEDIUM, 40, List.of(trace)),
                List.of(impact("account-api", ComponentImpact.State.ORIGIN, 0, List.of(trace))));

        assertThat(severity.score()).isEqualTo(20);
        assertThat(severity.level()).isEqualTo(IncidentSeverity.Level.MEDIUM);
    }

    @Test
    void severityIsLowWhenNoEscalationFactorExists() {
        var severity = calculator.calculate(topology(Map.of()),
                new OriginAssessment("postgres", OriginAssessment.Confidence.LOW, 0, List.of()),
                List.of(impact("postgres", ComponentImpact.State.ORIGIN, 0, List.of())));
        assertThat(severity.score()).isZero();
        assertThat(severity.level()).isEqualTo(IncidentSeverity.Level.LOW);
    }

    private ComponentImpact impact(String id, ComponentImpact.State state, Integer distance, List<EvidenceSignal> evidence) {
        return new ComponentImpact(id, state, distance, List.of(id), evidence);
    }

    private DependencyTopology topology(Map<String,String> postgresMetadata) {
        return DependencyTopology.builder().applicationId("document-platform").environment("local")
                .nodes(List.of(
                        node("postgres", ComponentType.DATABASE, postgresMetadata),
                        node("storage-api", ComponentType.SERVICE, Map.of()),
                        node("account-api", ComponentType.SERVICE, Map.of()),
                        node("checkout-api", ComponentType.SERVICE, Map.of()),
                        node("external-service", ComponentType.EXTERNAL_SYSTEM, Map.of())))
                .build();
    }

    private ComponentNode node(String id, ComponentType type, Map<String,String> metadata) {
        return ComponentNode.builder().id(id).name(id).type(type).metadata(metadata).build();
    }
}
