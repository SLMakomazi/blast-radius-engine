package com.madlanga.blastradius.adapters.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blast-radius.ai.gemini")
public record GeminiProperties(
        boolean enabled,
        String apiKey,
        String model,
        String baseUrl,
        int timeoutSeconds) {
}
