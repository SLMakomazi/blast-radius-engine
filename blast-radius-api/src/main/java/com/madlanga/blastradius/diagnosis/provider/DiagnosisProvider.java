package com.madlanga.blastradius.diagnosis.provider;

public interface DiagnosisProvider {
    DiagnosisResponse diagnose(DiagnosisRequest context);
}
