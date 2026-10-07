package com.madlanga.blastradius.diagnosis.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blast-radius.ai.gemini")
public record GeminiProperties(
        boolean enabled,
        String apiKey,
        String model,
        List<String> fallbackModels,
        String baseUrl,
        int timeoutSeconds) {

    public GeminiProperties {
        fallbackModels = fallbackModels == null ? List.of() : List.copyOf(fallbackModels);
    }
}
