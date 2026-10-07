package com.madlanga.blastradius.diagnosis.provider;

import com.madlanga.blastradius.diagnosis.dto.DiagnosisResponse;
import com.madlanga.blastradius.diagnosis.provider.DiagnosisProvider;
import com.madlanga.blastradius.diagnosis.dto.DiagnosisRequest;
import java.util.List;

public final class DeterministicDiagnosisProvider implements DiagnosisProvider {
    @Override
    public DiagnosisResponse diagnose(DiagnosisRequest context) {
        String origin = context.origin().component();
        long observed = context.impacts().stream()
                .filter(impact -> "OBSERVED".equals(impact.state()) || "UNEXPECTED".equals(impact.state()))
                .count();

        return new DiagnosisResponse(
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
