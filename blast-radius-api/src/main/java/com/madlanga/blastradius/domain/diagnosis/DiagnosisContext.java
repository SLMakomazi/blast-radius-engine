package com.madlanga.blastradius.domain.diagnosis;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DiagnosisContext(
        String applicationId,
        String environment,
        Instant from,
        Instant to,
        Origin origin,
        Coverage coverage,
        Severity severity,
        List<Impact> impacts,
        List<Evidence> timeline,
        Experiment experiment,
        List<String> warnings) {

    public DiagnosisContext {
        impacts = impacts == null ? List.of() : List.copyOf(impacts);
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record Origin(String component, String confidence, int evidenceScore) {}
    public record Coverage(String logs, String metrics, String traces, String health, boolean fullyCovered) {}
    public record Severity(String level, int score, List<String> reasons) {
        public Severity { reasons = reasons == null ? List.of() : List.copyOf(reasons); }
    }
    public record Impact(String component, String state, Integer distance, List<String> path, List<Evidence> evidence) {
        public Impact {
            path = path == null ? List.of() : List.copyOf(path);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }
    public record Evidence(Instant timestamp, String component, String family, String signal, String evidenceId) {}
    public record Experiment(
            String experimentId,
            String containment,
            List<String> expectedImpact,
            List<String> observedExpectedImpact,
            List<String> expectedButUnobserved,
            List<String> unexpectedImpact) {
        public Experiment {
            expectedImpact = copy(expectedImpact);
            observedExpectedImpact = copy(observedExpectedImpact);
            expectedButUnobserved = copy(expectedButUnobserved);
            unexpectedImpact = copy(unexpectedImpact);
        }
        private static List<String> copy(List<String> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
    }
}
