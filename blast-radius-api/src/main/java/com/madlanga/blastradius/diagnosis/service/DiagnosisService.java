package com.madlanga.blastradius.diagnosis.service;

import com.madlanga.blastradius.diagnosis.dto.DiagnosisRequest;
import com.madlanga.blastradius.diagnosis.dto.DiagnosisResponse;
import com.madlanga.blastradius.diagnosis.mapper.DiagnosisRequestMapper;
import com.madlanga.blastradius.diagnosis.provider.DiagnosisProvider;

import com.madlanga.blastradius.incident.model.IncidentAnalysis;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiagnosisService {
    private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);
    private final DiagnosisProvider primary;
    private final DiagnosisProvider fallback;
    private final DiagnosisRequestMapper contextMapper;

    public DiagnosisService(
            DiagnosisProvider primary,
            DiagnosisProvider fallback,
            DiagnosisRequestMapper contextMapper) {
        this.primary = primary;
        this.fallback = fallback;
        this.contextMapper = contextMapper;
    }

    public DiagnosisResponse diagnose(IncidentAnalysis analysis) {
        return diagnose(contextMapper.from(analysis));
    }

    public DiagnosisResponse diagnose(DiagnosisRequest context) {
        try {
            return primary.diagnose(context);
        } catch (RuntimeException e) {
            log.warn(
                    "AI diagnosis provider failed; returning deterministic fallback. application={} environment={} origin={} reason={}",
                    context.applicationId(),
                    context.environment(),
                    context.origin() == null ? "unknown" : context.origin().component(),
                    safeReason(e),
                    e);
            var fallbackDiagnosis = fallback.diagnose(context);
            var limitations = new ArrayList<>(fallbackDiagnosis.limitations());
            limitations.add("AI provider unavailable: " + safeReason(e));
            return new DiagnosisResponse(
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
