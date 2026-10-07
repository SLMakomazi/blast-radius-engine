package com.madlanga.blastradius.diagnosis.provider;

import com.madlanga.blastradius.diagnosis.dto.DiagnosisRequest;
import com.madlanga.blastradius.diagnosis.dto.DiagnosisResponse;

public interface DiagnosisProvider {
    DiagnosisResponse diagnose(DiagnosisRequest context);
}
