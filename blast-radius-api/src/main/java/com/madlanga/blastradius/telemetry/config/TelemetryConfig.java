package com.madlanga.blastradius.telemetry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for telemetry adapters and their shared infrastructure.
 */
@Configuration
@EnableConfigurationProperties({
        TelemetryConfig.LokiProperties.class,
        TelemetryConfig.PrometheusProperties.class,
        TelemetryConfig.TempoProperties.class,
        TelemetryConfig.ActuatorHealthProperties.class,
        TelemetryConfig.DatabaseHealthProperties.class
})
public class TelemetryConfig {

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
    // Provider settings -------------------------------------------------------

    @ConfigurationProperties(prefix = "blast-radius.telemetry.loki")
    public static class LokiProperties {
        private String baseUrl = "http://loki:3100";
        private int limit = 500;
        private Duration timeout = Duration.ofSeconds(10);
        private String providerId = "local-loki";
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public String getProviderId() { return providerId; }
        public void setProviderId(String providerId) { this.providerId = providerId; }
    }

    @ConfigurationProperties(prefix = "blast-radius.telemetry.prometheus")
    public static class PrometheusProperties {
        private String baseUrl = "http://prometheus:9090";
        private Duration timeout = Duration.ofSeconds(10);
        private String providerId = "local-prometheus";
        private List<String> metricSelectors = List.of(
                "http_server_requests_seconds_count", "http_server_requests_seconds_sum",
                "http_server_requests_seconds_max", "http_client_requests_seconds_count",
                "http_client_requests_seconds_sum", "hikaricp_connections_active",
                "hikaricp_connections_pending", "hikaricp_connections_timeout_total",
                "jvm_memory_used_bytes", "process_cpu_usage");
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public String getProviderId() { return providerId; }
        public void setProviderId(String providerId) { this.providerId = providerId; }
        public List<String> getMetricSelectors() { return metricSelectors; }
        public void setMetricSelectors(List<String> metricSelectors) { this.metricSelectors = metricSelectors; }
    }

    @ConfigurationProperties(prefix = "blast-radius.telemetry.tempo")
    public static class TempoProperties {
        private String baseUrl = "http://tempo:3200";
        private Duration timeout = Duration.ofSeconds(10);
        private String providerId = "local-tempo";
        private int searchLimit = 20;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public String getProviderId() { return providerId; }
        public void setProviderId(String providerId) { this.providerId = providerId; }
        public int getSearchLimit() { return searchLimit; }
        public void setSearchLimit(int searchLimit) { this.searchLimit = searchLimit; }
    }

    @ConfigurationProperties(prefix = "blast-radius.telemetry.database-health")
    public static class DatabaseHealthProperties {
        private String service = "postgres";
        private String url = "jdbc:postgresql://postgres:5432/synthetic_lab?connectTimeout=2&socketTimeout=3";
        private String username = "synthetic_lab";
        private String password = "";
        private Duration timeout = Duration.ofSeconds(3);
        private String providerId = "local-postgres";
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public String getProviderId() { return providerId; }
        public void setProviderId(String providerId) { this.providerId = providerId; }
    }

    @ConfigurationProperties(prefix = "blast-radius.telemetry.health")
    public static class ActuatorHealthProperties {
        private Duration timeout = Duration.ofSeconds(5);
        private String providerId = "local-actuator";
        private Map<String, String> endpoints = new LinkedHashMap<>(Map.of(
                "blast-radius-api", "http://blast-radius-api:8080",
                "payment-service", "http://payment-service:8081",
                "customer-service", "http://customer-service:8082",
                "document-service", "http://document-service:8083"));
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public String getProviderId() { return providerId; }
        public void setProviderId(String providerId) { this.providerId = providerId; }
        public Map<String, String> getEndpoints() { return endpoints; }
        public void setEndpoints(Map<String, String> endpoints) { this.endpoints = endpoints; }
    }
}
