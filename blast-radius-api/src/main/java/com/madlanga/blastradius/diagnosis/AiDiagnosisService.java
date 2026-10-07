package com.madlanga.blastradius.diagnosis;

import com.madlanga.blastradius.incident.domain.IncidentAnalysis;
import java.util.ArrayList;

public final class AiDiagnosisService {
    private final AiDiagnosisProvider primary;
    private final AiDiagnosisProvider fallback;
    private final DiagnosisContextMapper contextMapper;

    public AiDiagnosisService(
            AiDiagnosisProvider primary,
            AiDiagnosisProvider fallback,
            DiagnosisContextMapper contextMapper) {
        this.primary = primary;
        this.fallback = fallback;
        this.contextMapper = contextMapper;
    }

    public AiDiagnosis diagnose(IncidentAnalysis analysis) {
        return diagnose(contextMapper.from(analysis));
    }

    public AiDiagnosis diagnose(DiagnosisContext context) {
        try {
            return primary.diagnose(context);
        } catch (RuntimeException e) {
            var fallbackDiagnosis = fallback.diagnose(context);
            var limitations = new ArrayList<>(fallbackDiagnosis.limitations());
            limitations.add("AI provider unavailable: " + safeReason(e));
            return new AiDiagnosis(
                    fallbackDiagnosis.provider(),
                    fallbackDiagnosis.model(),
                    fallbackDiagnosis.summary(),
                    fallbackDiagnosis.probableCause(),
                    fallbackDiagnosis.immediateActions(),
                    fallbackDiagnosis.mediumTermActions(),
                    fallbackDiagnosis.strategicActions(),
                    limitations);
        }
    }

    private String safeReason(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
