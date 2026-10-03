package com.madlanga.blastradius.adapters.ai;

import com.madlanga.blastradius.ports.AiDiagnosisPort;
import com.madlanga.blastradius.service.AiDiagnosisService;
import com.madlanga.blastradius.service.DeterministicDiagnosisAdapter;
import com.madlanga.blastradius.service.DiagnosisContextFactory;
import com.madlanga.blastradius.service.StoredAnalysisDiagnosisContextMapper;
import com.madlanga.blastradius.sanitization.TelemetrySanitizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
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
            @Qualifier("aiDiagnosisPort") AiDiagnosisPort aiDiagnosisPort,
            @Qualifier("deterministicDiagnosisAdapter") DeterministicDiagnosisAdapter fallback,
            DiagnosisContextFactory contextFactory) {
        return new AiDiagnosisService(aiDiagnosisPort, fallback, contextFactory);
    }
}
