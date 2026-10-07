package com.madlanga.blastradius.diagnosis.infrastructure;

import com.madlanga.blastradius.diagnosis.application.port.AiDiagnosisPort;
import com.madlanga.blastradius.diagnosis.application.AiDiagnosisService;
import com.madlanga.blastradius.diagnosis.application.DiagnosisContextFactory;
import com.madlanga.blastradius.diagnosis.application.StoredAnalysisDiagnosisContextMapper;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class AiDiagnosisConfiguration {

    @Bean
    DiagnosisContextFactory diagnosisContextFactory() {
        return new DiagnosisContextFactory();
    }

    @Bean
    StoredAnalysisDiagnosisContextMapper storedAnalysisDiagnosisContextMapper(JsonMapper jsonMapper,
            TelemetrySanitizer sanitizer) {
        return new StoredAnalysisDiagnosisContextMapper(jsonMapper, sanitizer);
    }

    @Bean
    DeterministicDiagnosisAdapter deterministicDiagnosisAdapter() {
        return new DeterministicDiagnosisAdapter();
    }

    @Bean
    AiDiagnosisPort aiDiagnosisPort(GeminiProperties properties, JsonMapper jsonMapper,
            DeterministicDiagnosisAdapter fallback) {
        if (!properties.enabled()) return fallback;
        return new GeminiDiagnosisAdapter(properties, jsonMapper);
    }

    @Bean
    AiDiagnosisService aiDiagnosisService(
            AiDiagnosisPort aiDiagnosisPort,
            DeterministicDiagnosisAdapter fallback,
            DiagnosisContextFactory contextFactory) {
        return new AiDiagnosisService(aiDiagnosisPort, fallback, contextFactory);
    }
}
