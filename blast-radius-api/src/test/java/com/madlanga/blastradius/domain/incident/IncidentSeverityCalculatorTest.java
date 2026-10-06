package com.madlanga.blastradius.domain.incident;

import static org.assertj.core.api.Assertions.assertThat;
import com.madlanga.blastradius.domain.incident.*;
import com.madlanga.blastradius.topology.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncidentSeverityCalculatorTest {
    private final IncidentSeverityCalculator calculator = new IncidentSeverityCalculator();

    @Test
    void scoresThreeHopObservedAvailabilityFailureAsCritical() {
        var topology = topology(Map.of());
        var health = new EvidenceSignal(Instant.parse("2026-10-03T04:00:00Z"), "document-service", "HEALTH", "health DOWN", "h1");
        var impacts = List.of(
                impact("postgres", ObservedState.ORIGIN, 0, List.of()),
                impact("document-service", ObservedState.OBSERVED, 1, List.of(health)),
                impact("customer-service", ObservedState.OBSERVED, 2, List.of()),
                impact("payment-service", ObservedState.OBSERVED, 3, List.of()));
        var severity = calculator.calculate(topology, new OriginAssessment("postgres", ConfidenceLevel.HIGH, 100, List.of()), impacts);
        assertThat(severity.score()).isEqualTo(80);
        assertThat(severity.level()).isEqualTo(SeverityLevel.CRITICAL);
    }

    @Test
    void theoreticalDepthDoesNotEscalateIncidentSeverityWithoutObservedPropagation() {
        var impacts = List.of(
                impact("postgres", ObservedState.ORIGIN, 0, List.of()),
                impact("document-service", ObservedState.THEORETICAL_ONLY, 1, List.of()),
                impact("customer-service", ObservedState.THEORETICAL_ONLY, 2, List.of()),
                impact("payment-service", ObservedState.THEORETICAL_ONLY, 3, List.of()));
        var severity = calculator.calculate(topology(Map.of()),
                new OriginAssessment("postgres", ConfidenceLevel.LOW, 0, List.of()), impacts);
        assertThat(severity.score()).isZero();
        assertThat(severity.level()).isEqualTo(SeverityLevel.LOW);
        assertThat(severity.reasons()).containsExactly("no configured severity escalation factor was observed");
    }

    @Test
    void unexpectedImpactAndCriticalOriginEscalateSeverity() {
        var impacts = List.of(
                impact("postgres", ObservedState.ORIGIN, 0, List.of()),
                impact("document-service", ObservedState.OBSERVED, 1, List.of()),
                impact("external-service", ObservedState.UNEXPECTED, null, List.of()));
        var severity = calculator.calculate(topology(Map.of("criticality","HIGH")),
                new OriginAssessment("postgres", ConfidenceLevel.HIGH, 100, List.of()), impacts);
        assertThat(severity.score()).isEqualTo(60);
        assertThat(severity.level()).isEqualTo(SeverityLevel.HIGH);
        assertThat(severity.reasons()).anyMatch(r -> r.contains("outside the theoretical radius"));
    }

    @Test
    void directOriginAvailabilityFailureIsAtLeastHigh() {
        var health = new EvidenceSignal(Instant.parse("2026-10-04T06:00:00Z"),
                "customer-service", "HEALTH", "health UNREACHABLE", "h-customer");
        var severity = calculator.calculate(topology(Map.of()),
                new OriginAssessment("customer-service", ConfidenceLevel.HIGH, 100, List.of(health)),
                List.of(
                        impact("customer-service", ObservedState.ORIGIN, 0, List.of(health)),
                        impact("payment-service", ObservedState.OBSERVED, 1, List.of())));

        assertThat(severity.score()).isEqualTo(60);
        assertThat(severity.level()).isEqualTo(SeverityLevel.HIGH);
        assertThat(severity.reasons()).contains(
                "failure evidence is confirmed on the origin component",
                "availability/health degradation is observed",
                "failure has propagated beyond the origin component");
    }

    @Test
    void directOriginFailureWithoutAvailabilityEvidenceIsMedium() {
        var trace = new EvidenceSignal(Instant.parse("2026-10-04T06:00:00Z"),
                "customer-service", "TRACE", "error span: request", "t-customer");
        var severity = calculator.calculate(topology(Map.of()),
                new OriginAssessment("customer-service", ConfidenceLevel.MEDIUM, 40, List.of(trace)),
                List.of(impact("customer-service", ObservedState.ORIGIN, 0, List.of(trace))));

        assertThat(severity.score()).isEqualTo(20);
        assertThat(severity.level()).isEqualTo(SeverityLevel.MEDIUM);
    }

    @Test
    void severityIsLowWhenNoEscalationFactorExists() {
        var severity = calculator.calculate(topology(Map.of()),
                new OriginAssessment("postgres", ConfidenceLevel.LOW, 0, List.of()),
                List.of(impact("postgres", ObservedState.ORIGIN, 0, List.of())));
        assertThat(severity.score()).isZero();
        assertThat(severity.level()).isEqualTo(SeverityLevel.LOW);
    }

    private ComponentImpact impact(String id, ObservedState state, Integer distance, List<EvidenceSignal> evidence) {
        return new ComponentImpact(id, state, distance, List.of(id), evidence);
    }

    private DependencyTopology topology(Map<String,String> postgresMetadata) {
        return DependencyTopology.builder().applicationId("document-platform").environment("local")
                .nodes(List.of(
                        node("postgres", ComponentType.DATABASE, postgresMetadata),
                        node("document-service", ComponentType.SERVICE, Map.of()),
                        node("customer-service", ComponentType.SERVICE, Map.of()),
                        node("payment-service", ComponentType.SERVICE, Map.of()),
                        node("external-service", ComponentType.EXTERNAL_SYSTEM, Map.of())))
                .build();
    }

    private ComponentNode node(String id, ComponentType type, Map<String,String> metadata) {
        return ComponentNode.builder().id(id).name(id).type(type).metadata(metadata).build();
    }
}
