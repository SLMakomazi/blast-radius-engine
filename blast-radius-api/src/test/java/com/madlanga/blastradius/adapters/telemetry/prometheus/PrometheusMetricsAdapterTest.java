package com.madlanga.blastradius.adapters.telemetry.prometheus;

import com.madlanga.blastradius.domain.evidence.CoverageStatus;
import com.madlanga.blastradius.domain.evidence.MetricEvidence;
import com.madlanga.blastradius.domain.evidence.TelemetryQuery;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrometheusMetricsAdapterTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> uriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private PrometheusMetricsAdapter adapter;

    private static final Instant FROM = Instant.parse("2026-10-01T10:30:00Z");
    private static final Instant TO   = Instant.parse("2026-10-01T10:35:00Z");

    @BeforeEach
    void setUp() {
        PrometheusProperties props = new PrometheusProperties();
        // Use a single metric selector for deterministic testing
        props.setMetricSelectors(List.of("http_server_requests_seconds_count"));

        TelemetrySanitizer sanitizer = new TelemetrySanitizer();

        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        adapter = new PrometheusMetricsAdapter(props, sanitizer, restClientBuilder);
    }

    private TelemetryQuery query() {
        return TelemetryQuery.builder()
                .applicationId("document-platform")
                .environment("local")
                .from(FROM)
                .to(TO)
                .build();
    }

    @Test
    void returnsUnavailableWhenPrometheusIsUnreachable() {
        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>any());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doThrow(new ResourceAccessException("Connection refused"))
                .when(responseSpec).body(PrometheusResponse.class);

        PrometheusMetricsAdapter.MetricAdapterResult result = adapter.fetchMetrics(query());

        assertEquals(CoverageStatus.UNAVAILABLE, result.getCoverage());
        assertTrue(result.getMetrics().isEmpty());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void mapsMatrixSeriesesToNormalizedMetricEvidence() {
        PrometheusResponse response = new PrometheusResponse();
        response.status = "success";
        response.data = new PrometheusResponse.Data();

        PrometheusResponse.Series series = new PrometheusResponse.Series();
        series.metric = Map.of(
                "service", "document-service",
                "status", "503",
                "uri", "/api/documents");
        // [unix-epoch-float, value-string]
        series.values = List.of(
                List.of(1727776262.0, "14"),
                List.of(1727776277.0, "16"));
        response.data.result = List.of(series);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>any());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(PrometheusResponse.class);

        PrometheusMetricsAdapter.MetricAdapterResult result = adapter.fetchMetrics(query());

        assertEquals(CoverageStatus.AVAILABLE, result.getCoverage());
        assertEquals(2, result.getMetrics().size());

        MetricEvidence m = result.getMetrics().get(0);
        assertEquals("document-service", m.getService());
        assertEquals("http.server.requests.seconds.count", m.getName());
        assertEquals(14.0, m.getValue());
        assertEquals("503", m.getDimensions().get("status"));
        assertEquals("/api/documents", m.getDimensions().get("uri"));
        assertEquals("local-prometheus", m.getProvenance().getProvider());
    }

    @Test
    void preservesTimestampFromPrometheusPoint() {
        PrometheusResponse response = new PrometheusResponse();
        response.status = "success";
        response.data = new PrometheusResponse.Data();
        PrometheusResponse.Series series = new PrometheusResponse.Series();
        series.metric = Map.of("service", "payment-service");
        // Unix epoch seconds for 2026-10-01T10:31:02Z = 1727776262
        series.values = List.of(List.of(1727776262.0, "170"));
        response.data.result = List.of(series);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>any());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(PrometheusResponse.class);

        PrometheusMetricsAdapter.MetricAdapterResult result = adapter.fetchMetrics(query());

        assertFalse(result.getMetrics().isEmpty());
        Instant ts = result.getMetrics().get(0).getTimestamp();
        assertEquals(1727776262L, ts.getEpochSecond());
    }

    @Test
    void returnsUnavailableWhenSeriesIsEmpty() {
        PrometheusResponse response = new PrometheusResponse();
        response.status = "success";
        response.data = new PrometheusResponse.Data();
        response.data.result = List.of();

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>any());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(PrometheusResponse.class);

        PrometheusMetricsAdapter.MetricAdapterResult result = adapter.fetchMetrics(query());

        assertEquals(CoverageStatus.UNAVAILABLE, result.getCoverage());
    }

    @Test
    void sanitizesMetricLabelsThroughSanitizer() {
        PrometheusResponse response = new PrometheusResponse();
        response.status = "success";
        response.data = new PrometheusResponse.Data();
        PrometheusResponse.Series series = new PrometheusResponse.Series();
        // In a real scenario labels don't contain passwords, but the adapter sanitizes defensively
        series.metric = Map.of("service", "document-service", "password", "synthetic-pass");
        series.values = List.of(List.of(1727776262.0, "5"));
        response.data.result = List.of(series);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>any());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(PrometheusResponse.class);

        PrometheusMetricsAdapter.MetricAdapterResult result = adapter.fetchMetrics(query());

        assertFalse(result.getMetrics().isEmpty());
        // The password label value must be redacted
        String passValue = result.getMetrics().get(0).getDimensions().get("password");
        assertNotEquals("synthetic-pass", passValue);
    }
}
