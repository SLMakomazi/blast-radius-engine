package com.madlanga.blastradius.diagnosis.application.port;

import com.madlanga.blastradius.diagnosis.domain.AiDiagnosis;
import com.madlanga.blastradius.diagnosis.domain.DiagnosisContext;

public interface AiDiagnosisPort {
    AiDiagnosis diagnose(DiagnosisContext context);
}
