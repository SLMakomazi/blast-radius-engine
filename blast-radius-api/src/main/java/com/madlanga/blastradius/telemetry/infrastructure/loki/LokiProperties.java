package com.madlanga.blastradius.telemetry.infrastructure.loki;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * External configuration for the Loki log adapter.
 *
 * <p>Bound from {@code blast-radius.telemetry.loki.*} in {@code application.yml}.
 * Defaults target the local lab topology; override for other environments.</p>
 */
@ConfigurationProperties(prefix = "blast-radius.telemetry.loki")
public class LokiProperties {

    /** Loki base URL. No trailing slash. */
    private String baseUrl = "http://loki:3100";

    /** Maximum number of log lines to retrieve per query. */
    private int limit = 500;

    /** HTTP request timeout for Loki queries. */
    private Duration timeout = Duration.ofSeconds(10);

    /** Provider identifier included in EvidenceProvenance. */
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
