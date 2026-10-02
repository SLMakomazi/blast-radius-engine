package com.madlanga.blastradius.adapters.telemetry.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * External configuration for the Actuator health adapter.
 * Bound from {@code blast-radius.telemetry.health.*} in {@code application.yml}.
 *
 * <p>Endpoints are a map of logical component name to base URL.
 * Additional components can be added without code changes.</p>
 */
@ConfigurationProperties(prefix = "blast-radius.telemetry.health")
public class ActuatorHealthProperties {

    /** HTTP request timeout per health probe. */
    private Duration timeout = Duration.ofSeconds(5);

    /** Provider identifier included in EvidenceProvenance. */
    private String providerId = "local-actuator";

    /**
     * Map of component name → actuator base URL.
     * The adapter appends {@code /actuator/health} to each URL.
     *
     * <p>Default values target the local lab topology.
     * Override in {@code application.yml} for other environments.</p>
     */
    private Map<String, String> endpoints = new LinkedHashMap<>(Map.of(
            "blast-radius-api",  "http://blast-radius-api:8080",
            "payment-service",   "http://payment-service:8081",
            "customer-service",  "http://customer-service:8082",
            "document-service",  "http://document-service:8083"
    ));

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public Map<String, String> getEndpoints() { return endpoints; }
    public void setEndpoints(Map<String, String> endpoints) { this.endpoints = endpoints; }
}
