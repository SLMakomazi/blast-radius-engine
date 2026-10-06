package com.madlanga.blastradius.incident.application.port;

import com.madlanga.blastradius.incident.domain.containment.FailureExperiment;
import java.util.Optional;

public interface FailureExperimentProvider {
    Optional<FailureExperiment> getExperiment(String experimentId);
}
