package com.madlanga.blastradius.diagnosis;

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
public class DiagnosisConfiguration {

    @Bean
    DiagnosisContextMapper diagnosisContextMapper(JsonMapper jsonMapper, TelemetrySanitizer sanitizer) {
        return new DiagnosisContextMapper(jsonMapper, sanitizer);
    }

    @Bean
    DeterministicDiagnosisProvider deterministicDiagnosisProvider() {
        return new DeterministicDiagnosisProvider();
    }

    @Bean
    AiDiagnosisProvider aiDiagnosisProvider(
            GeminiProperties properties,
            JsonMapper jsonMapper,
            DeterministicDiagnosisProvider fallback) {
        if (!properties.enabled()) {
            return fallback;
        }
        return new GeminiDiagnosisProvider(properties, jsonMapper);
    }

    @Bean
    AiDiagnosisService aiDiagnosisService(
            @Qualifier("aiDiagnosisProvider") AiDiagnosisProvider primary,
            @Qualifier("deterministicDiagnosisProvider") DeterministicDiagnosisProvider fallback,
            DiagnosisContextMapper contextMapper) {
        return new AiDiagnosisService(primary, fallback, contextMapper);
    }
}
