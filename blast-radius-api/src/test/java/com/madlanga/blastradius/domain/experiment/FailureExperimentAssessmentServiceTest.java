package com.madlanga.blastradius.domain.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import com.madlanga.blastradius.domain.experiment.*;
import com.madlanga.blastradius.domain.incident.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FailureExperimentAssessmentServiceTest {
    private final FailureExperimentAssessmentService service = new FailureExperimentAssessmentService();

    @Test
    void reportsContainmentHeldWhenObservedImpactStaysInsideBoundary() {
        var experiment = new FailureExperiment("chaos-1", "postgres",
                Set.of("document-service","customer-service"), Set.of("postgres","document-service","customer-service"));
        var result = service.assess(experiment, List.of(
                impact("postgres",ObservedState.ORIGIN),
                impact("document-service",ObservedState.OBSERVED),
                impact("customer-service",ObservedState.OBSERVED)), true);
        assertThat(result.containment()).isEqualTo(ContainmentStatus.HELD);
        assertThat(result.unexpectedImpact()).isEmpty();
        assertThat(result.expectedButUnobserved()).isEmpty();
    }

    @Test
    void reportsContainmentBreachAndUnexpectedImpactOutsideBoundary() {
        var experiment = new FailureExperiment("chaos-2", "postgres",
                Set.of("document-service"), Set.of("postgres","document-service"));
        var result = service.assess(experiment, List.of(
                impact("postgres",ObservedState.ORIGIN),
                impact("document-service",ObservedState.OBSERVED),
                impact("payment-service",ObservedState.UNEXPECTED)), true);
        assertThat(result.containment()).isEqualTo(ContainmentStatus.BREACHED);
        assertThat(result.unexpectedImpact()).containsExactly("payment-service");
    }

    @Test
    void partialTelemetryMakesOtherwiseHeldContainmentInconclusive() {
        var experiment = new FailureExperiment("chaos-3", "postgres",
                Set.of("document-service"), Set.of("postgres","document-service"));
        var result = service.assess(experiment, List.of(
                impact("postgres",ObservedState.ORIGIN),
                impact("document-service",ObservedState.OBSERVED)), false);
        assertThat(result.containment()).isEqualTo(ContainmentStatus.INCONCLUSIVE);
    }

    private ComponentImpact impact(String id, ObservedState state) {
        return new ComponentImpact(id,state,0,List.of(id),List.of());
    }
}
