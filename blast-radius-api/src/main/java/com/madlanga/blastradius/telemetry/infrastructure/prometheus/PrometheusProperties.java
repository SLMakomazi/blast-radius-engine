package com.madlanga.blastradius.telemetry.infrastructure.prometheus;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * External configuration for the Prometheus metrics adapter.
 * Bound from {@code blast-radius.telemetry.prometheus.*} in {@code application.yml}.
 */
@ConfigurationProperties(prefix = "blast-radius.telemetry.prometheus")
public class PrometheusProperties {

    /** Prometheus base URL. No trailing slash. */
    private String baseUrl = "http://prometheus:9090";

    /** HTTP request timeout for Prometheus queries. */
    private Duration timeout = Duration.ofSeconds(10);

    /** Provider identifier included in EvidenceProvenance. */
    private String providerId = "local-prometheus";

    /**
     * Metric names (or name prefixes) to retrieve for the incident window.
     * These cover the evidence concepts required by Phase 4: request volume,
     * latency, error rate, HTTP status behaviour, zero-traffic, JVM/process info,
     * downstream request metrics, and database pool metrics.
     */
    private List<String> metricSelectors = List.of(
            "http_server_requests_seconds_count",
            "http_server_requests_seconds_sum",
            "http_server_requests_seconds_max",
            "http_client_requests_seconds_count",
            "http_client_requests_seconds_sum",
            "hikaricp_connections_active",
            "hikaricp_connections_pending",
            "hikaricp_connections_timeout_total",
            "jvm_memory_used_bytes",
            "process_cpu_usage"
    );

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public List<String> getMetricSelectors() { return metricSelectors; }
    public void setMetricSelectors(List<String> metricSelectors) { this.metricSelectors = metricSelectors; }
}
