package com.madlanga.blastradius.telemetry.infrastructure.tempo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * External configuration for the Tempo trace adapter.
 * Bound from {@code blast-radius.telemetry.tempo.*} in {@code application.yml}.
 */
@ConfigurationProperties(prefix = "blast-radius.telemetry.tempo")
public class TempoProperties {

    /** Tempo base URL. No trailing slash. */
    private String baseUrl = "http://tempo:3200";

    /** HTTP request timeout for Tempo queries. */
    private Duration timeout = Duration.ofSeconds(10);

    /** Provider identifier included in EvidenceProvenance. */
    private String providerId = "local-tempo";

    /** Maximum number of traces to retrieve per search query. */
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
