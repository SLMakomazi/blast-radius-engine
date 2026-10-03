package com.madlanga.blastradius.ports;

import com.madlanga.blastradius.domain.diagnosis.AiDiagnosis;
import com.madlanga.blastradius.domain.diagnosis.DiagnosisContext;

public interface AiDiagnosisPort {
    AiDiagnosis diagnose(DiagnosisContext context);
}
