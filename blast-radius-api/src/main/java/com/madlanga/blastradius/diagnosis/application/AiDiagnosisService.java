package com.madlanga.blastradius.diagnosis.application;

import com.madlanga.blastradius.diagnosis.domain.AiDiagnosis;
import com.madlanga.blastradius.incident.domain.IncidentAnalysis;
import com.madlanga.blastradius.diagnosis.application.port.AiDiagnosisPort;

public final class AiDiagnosisService {
    private final AiDiagnosisPort primary;
    private final AiDiagnosisPort fallback;
    private final DiagnosisContextFactory contextFactory;

    public AiDiagnosisService(AiDiagnosisPort primary, AiDiagnosisPort fallback, DiagnosisContextFactory contextFactory) {
        this.primary = primary;
        this.fallback = fallback;
        this.contextFactory = contextFactory;
    }

    public AiDiagnosis diagnose(IncidentAnalysis analysis) {
        return diagnose(contextFactory.from(analysis));
    }

    public AiDiagnosis diagnose(com.madlanga.blastradius.diagnosis.domain.DiagnosisContext context) {
        try {
            return primary.diagnose(context);
        } catch (RuntimeException e) {
            var fallbackDiagnosis = fallback.diagnose(context);
            var limitations = new java.util.ArrayList<>(fallbackDiagnosis.limitations());
            limitations.add("AI provider unavailable: " + safeReason(e));
            return new AiDiagnosis(
                    fallbackDiagnosis.provider(), fallbackDiagnosis.model(),
                    fallbackDiagnosis.summary(), fallbackDiagnosis.probableCause(),
                    fallbackDiagnosis.immediateActions(), fallbackDiagnosis.mediumTermActions(),
                    fallbackDiagnosis.strategicActions(), limitations);
        }
    }

    private String safeReason(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
