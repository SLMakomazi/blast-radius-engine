package com.madlanga.blastradius.diagnosis.config;

import com.madlanga.blastradius.diagnosis.mapper.DiagnosisRequestMapper;
import com.madlanga.blastradius.diagnosis.provider.DiagnosisProvider;
import com.madlanga.blastradius.diagnosis.service.DiagnosisService;

import com.madlanga.blastradius.diagnosis.provider.DeterministicDiagnosisProvider;
import com.madlanga.blastradius.diagnosis.provider.GeminiDiagnosisProvider;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class DiagnosisConfig {

    @Bean
    DiagnosisRequestMapper diagnosisRequestMapper(JsonMapper jsonMapper, TelemetrySanitizer sanitizer) {
        return new DiagnosisRequestMapper(jsonMapper, sanitizer);
    }

    @Bean
    DeterministicDiagnosisProvider deterministicDiagnosisProvider() {
        return new DeterministicDiagnosisProvider();
    }

    @Bean
    DiagnosisProvider diagnosisProvider(
            GeminiProperties properties,
            JsonMapper jsonMapper,
            DeterministicDiagnosisProvider fallback) {
        if (!properties.enabled()) {
            return fallback;
        }
        return new GeminiDiagnosisProvider(properties, jsonMapper);
    }

    @Bean
    DiagnosisService diagnosisService(
            @Qualifier("diagnosisProvider") DiagnosisProvider primary,
            @Qualifier("deterministicDiagnosisProvider") DeterministicDiagnosisProvider fallback,
            DiagnosisRequestMapper contextMapper) {
        return new DiagnosisService(primary, fallback, contextMapper);
    }
}
