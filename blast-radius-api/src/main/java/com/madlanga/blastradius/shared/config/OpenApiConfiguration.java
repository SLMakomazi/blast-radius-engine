package com.madlanga.blastradius.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI blastRadiusOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MadlangaAI Blast Radius API")
                .version("v1")
                .description("Deterministic blast-radius analysis. Topology calculates potential impact; telemetry proves observed impact; AI remains advisory."));
    }
}
