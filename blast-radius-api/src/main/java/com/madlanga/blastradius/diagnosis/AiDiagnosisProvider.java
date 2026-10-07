package com.madlanga.blastradius.diagnosis;

public interface AiDiagnosisProvider {
    AiDiagnosis diagnose(DiagnosisContext context);
}
