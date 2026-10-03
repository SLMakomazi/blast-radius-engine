package com.madlanga.blastradius.ports;

import com.madlanga.blastradius.domain.experiment.FailureExperiment;
import java.util.Optional;

public interface FailureExperimentProvider {
    Optional<FailureExperiment> getExperiment(String experimentId);
}
