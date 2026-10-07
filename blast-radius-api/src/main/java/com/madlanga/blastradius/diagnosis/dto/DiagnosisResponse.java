package com.madlanga.blastradius.diagnosis.dto;

import java.util.List;

public record DiagnosisResponse(
        String provider,
        String model,
        String summary,
        String probableCause,
        List<String> immediateActions,
        List<String> mediumTermActions,
        List<String> strategicActions,
        List<String> limitations) {

    public DiagnosisResponse {
        immediateActions = copy(immediateActions);
        mediumTermActions = copy(mediumTermActions);
        strategicActions = copy(strategicActions);
        limitations = copy(limitations);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
