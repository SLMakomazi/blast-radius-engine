package com.madlanga.blastradius.adapters.experiment;

import com.madlanga.blastradius.domain.experiment.FailureExperiment;
import com.madlanga.blastradius.ports.FailureExperimentProvider;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Phase 7 local experiment catalogue.
 * This describes controlled experiments; it does not inject failures.
 */
@Component
public final class LocalFailureExperimentProvider implements FailureExperimentProvider {
    private final Map<String, FailureExperiment> experiments = Map.of(
            "postgres-outage-local",
            new FailureExperiment(
                    "postgres-outage-local",
                    "postgres",
                    Set.of("document-service", "customer-service", "payment-service"),
                    Set.of("postgres", "document-service", "customer-service", "payment-service")));

    @Override
    public Optional<FailureExperiment> getExperiment(String experimentId) {
        if (experimentId == null || experimentId.isBlank()) return Optional.empty();
        return Optional.ofNullable(experiments.get(experimentId.trim()));
    }
}
