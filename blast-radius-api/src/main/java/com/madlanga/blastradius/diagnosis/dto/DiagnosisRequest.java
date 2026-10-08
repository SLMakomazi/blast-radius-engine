package com.madlanga.blastradius.diagnosis.dto;

import java.time.Instant;
import java.util.List;

public record DiagnosisRequest(
        String applicationId,
        String environment,
        Instant from,
        Instant to,
        Origin origin,
        Coverage coverage,
        Severity severity,
        List<Impact> impacts,
        List<Evidence> timeline,
        List<String> warnings) {

    public DiagnosisRequest {
        impacts = impacts == null ? List.of() : List.copyOf(impacts);
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record Origin(String component, String confidence, int evidenceScore) {}

    public record Coverage(String logs, String metrics, String traces, String health, boolean fullyCovered) {}

    public record Severity(String level, int score, List<String> reasons) {
        public Severity {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record Impact(String component, String state, Integer distance, List<String> path, List<Evidence> evidence) {
        public Impact {
            path = path == null ? List.of() : List.copyOf(path);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record Evidence(Instant timestamp, String component, String family, String signal, String evidenceId,
            String kind, String provider, String sourceRef, Instant collectedAt) {
        public Evidence(Instant timestamp, String component, String family, String signal, String evidenceId) {
            this(timestamp, component, family, signal, evidenceId, null, null, null, null);
        }
    }
}
