package com.madlanga.blastradius.telemetry.infrastructure.config;

import com.madlanga.blastradius.telemetry.infrastructure.health.ActuatorHealthProperties;
import com.madlanga.blastradius.telemetry.infrastructure.loki.LokiProperties;
import com.madlanga.blastradius.telemetry.infrastructure.prometheus.PrometheusProperties;
import com.madlanga.blastradius.telemetry.infrastructure.tempo.TempoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for telemetry adapters and their shared infrastructure.
 */
@Configuration
@EnableConfigurationProperties({
        LokiProperties.class,
        PrometheusProperties.class,
        TempoProperties.class,
        ActuatorHealthProperties.class
})
public class TelemetryAdapterConfig {

    /**
     * Provides the RestClient builder required by the telemetry adapters.
     *
     * Each adapter can mutate the injected builder when constructing its own
     * RestClient instance without coupling the domain model to an HTTP client.
     */
    @Bean
    RestClient.Builder restClientBuilder() {
        var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        var requests = new org.springframework.http.client.JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(java.time.Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requests);
    }
}
