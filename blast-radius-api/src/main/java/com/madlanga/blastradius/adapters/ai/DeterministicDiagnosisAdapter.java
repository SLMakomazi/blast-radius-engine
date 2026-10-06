package com.madlanga.blastradius.adapters.ai;

import com.madlanga.blastradius.domain.diagnosis.AiDiagnosis;
import com.madlanga.blastradius.domain.diagnosis.DiagnosisContext;
import com.madlanga.blastradius.ports.AiDiagnosisPort;
import java.util.List;

public final class DeterministicDiagnosisAdapter implements AiDiagnosisPort {
    @Override
    public AiDiagnosis diagnose(DiagnosisContext context) {
        String origin = context.origin().component();
        long observed = context.impacts().stream()
                .filter(i -> "OBSERVED".equals(i.state()) || "UNEXPECTED".equals(i.state()))
                .count();
        return new AiDiagnosis(
                "deterministic",
                "fallback-v1",
                "Deterministic analysis identified " + origin + " as the incident origin with "
                        + observed + " observed impacted component(s).",
                "The deterministic engine identified " + origin + " as the origin. AI diagnosis is unavailable.",
                List.of("Inspect " + origin + " and the evidence attached to the deterministic analysis."),
                List.of("Review propagation paths and telemetry coverage before changing dependent services."),
                List.of("Use the retained incident evidence to improve monitoring and resilience controls."),
                List.of("This fallback does not infer causes beyond deterministic evidence."));
    }
}
