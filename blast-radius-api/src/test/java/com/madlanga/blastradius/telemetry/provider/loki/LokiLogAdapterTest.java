package com.madlanga.blastradius.telemetry.provider.loki;

import com.madlanga.blastradius.telemetry.config.TelemetryConfig;

import tools.jackson.databind.json.JsonMapper;
import com.madlanga.blastradius.telemetry.model.CoverageStatus;
import com.madlanga.blastradius.telemetry.model.LogEvidence;
import com.madlanga.blastradius.telemetry.model.TelemetryQuery;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LokiLogAdapter} using controlled provider response fixtures.
 * No running Loki container is required.
 */
@ExtendWith(MockitoExtension.class)
class LokiLogAdapterTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> uriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private LokiLogAdapter adapter;
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    private static final Instant FROM = Instant.parse("2026-10-01T10:30:00Z");
    private static final Instant TO   = Instant.parse("2026-10-01T10:35:00Z");

    @BeforeEach
    void setUp() {
        TelemetryConfig.LokiProperties props = new TelemetryConfig.LokiProperties();
        TelemetrySanitizer sanitizer = new TelemetrySanitizer();

        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        adapter = new LokiLogAdapter(props, sanitizer, restClientBuilder, objectMapper);
    }

    private TelemetryQuery query() {
        return TelemetryQuery.builder()
                .applicationId("document-platform")
                .environment("local")
                .from(FROM)
                .to(TO)
                .componentFilter(List.of("checkout-api", "account-api", "storage-api"))
                .correlationId("phase3-healthy-abc")
                .build();
    }

    @Test
    void returnsUnavailableWhenLokiIsUnreachable() {
        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>any());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doThrow(new ResourceAccessException("Connection refused"))
                .when(responseSpec).body(LokiLogAdapter.LokiResponse.class);

        LokiLogAdapter.LogAdapterResult result = adapter.fetchLogs(query());

        assertEquals(CoverageStatus.UNAVAILABLE, result.getCoverage());
        assertTrue(result.getLogs().isEmpty());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().get(0).contains("Loki"));
    }

    @Test
    void returnsUnavailableWhenResponseIsNull() {
        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>any());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(null).when(responseSpec).body(LokiLogAdapter.LokiResponse.class);

        LokiLogAdapter.LogAdapterResult result = adapter.fetchLogs(query());

        assertEquals(CoverageStatus.UNAVAILABLE, result.getCoverage());
    }

    @Test
    void mapsStreamToNormalizedLogEvidence() {
        // Build a synthetic Loki response fixture in memory
        LokiLogAdapter.LokiResponse response = new LokiLogAdapter.LokiResponse();
        response.status = "success";
        response.data = new LokiLogAdapter.LokiResponse.Data();

        LokiLogAdapter.LokiResponse.Stream stream = new LokiLogAdapter.LokiResponse.Stream();
        stream.stream = java.util.Map.of(
                "service_name", "storage-api",
                "deployment_environment_name", "local");

        // Nanosecond timestamp for 2026-10-01T10:31:02Z
        long nanos = Instant.parse("2026-10-01T10:31:02Z").getEpochSecond() * 1_000_000_000L;
        String logJson = "{\"severityText\":\"ERROR\","
                + "\"body\":\"event=dependency_failed dependency=postgres code=DATABASE_UNAVAILABLE\","
                + "\"traceId\":\"61e1c07146fcb6829b35fca26be213c3\","
                + "\"spanId\":\"a08f368e31e4992c\","
                + "\"correlation_id\":\"phase3-healthy-abc\"}";
        stream.values = List.of(List.of(String.valueOf(nanos), logJson));
        response.data.result = List.of(stream);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>any());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(LokiLogAdapter.LokiResponse.class);

        LokiLogAdapter.LogAdapterResult result = adapter.fetchLogs(query());

        assertEquals(CoverageStatus.AVAILABLE, result.getCoverage());
        assertEquals(1, result.getLogs().size());

        LogEvidence log = result.getLogs().get(0);
        assertEquals("storage-api", log.getService());
        assertEquals("ERROR", log.getLevel());
        assertEquals("61e1c07146fcb6829b35fca26be213c3", log.getTraceId());
        assertEquals("a08f368e31e4992c", log.getSpanId());
        assertEquals("phase3-healthy-abc", log.getCorrelationId());
        assertNotNull(log.getProvenance());
        assertEquals("local-loki", log.getProvenance().getProvider());
    }

    @Test
    void preservesCorrelationId() {
        LokiLogAdapter.LokiResponse response = new LokiLogAdapter.LokiResponse();
        response.status = "success";
        response.data = new LokiLogAdapter.LokiResponse.Data();
        LokiLogAdapter.LokiResponse.Stream stream = new LokiLogAdapter.LokiResponse.Stream();
        stream.stream = java.util.Map.of("service_name", "checkout-api");
        long nanos = FROM.getEpochSecond() * 1_000_000_000L;
        String json = "{\"severityText\":\"INFO\",\"body\":\"ok\",\"correlation_id\":\"my-correlation-id\"}";
        stream.values = List.of(List.of(String.valueOf(nanos), json));
        response.data.result = List.of(stream);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>any());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(LokiLogAdapter.LokiResponse.class);

        LokiLogAdapter.LogAdapterResult result = adapter.fetchLogs(query());

        assertFalse(result.getLogs().isEmpty());
        assertEquals("my-correlation-id", result.getLogs().get(0).getCorrelationId());
    }


    @Test
    void mapsOtelStructuredMetadataFromRealLokiShape() {
        LokiLogAdapter.LokiResponse response = new LokiLogAdapter.LokiResponse();
        response.status = "success";
        response.data = new LokiLogAdapter.LokiResponse.Data();

        LokiLogAdapter.LokiResponse.Stream stream = new LokiLogAdapter.LokiResponse.Stream();
        stream.stream = java.util.Map.of(
                "service_name", "checkout-api",
                "deployment_environment_name", "local",
                "correlation_id", "phase4-integration-1790942164",
                "trace_id", "1657683429e6a0a411c08c1a8554444",
                "span_id", "39e32c2c76b45d5f",
                "severity_text", "INFO");

        long nanos = FROM.getEpochSecond() * 1_000_000_000L;
        stream.values = List.of(List.of(
                String.valueOf(nanos),
                "event=downstream_request dependency=account-api"));
        response.data.result = List.of(stream);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>any());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(LokiLogAdapter.LokiResponse.class);

        LokiLogAdapter.LogAdapterResult result = adapter.fetchLogs(query());

        assertEquals(CoverageStatus.AVAILABLE, result.getCoverage());
        assertEquals(1, result.getLogs().size());

        LogEvidence log = result.getLogs().get(0);
        assertEquals("checkout-api", log.getService());
        assertEquals("INFO", log.getLevel());
        assertEquals("phase4-integration-1790942164", log.getCorrelationId());
        assertEquals("1657683429e6a0a411c08c1a8554444", log.getTraceId());
        assertEquals("39e32c2c76b45d5f", log.getSpanId());
        assertEquals("event=downstream_request dependency=account-api", log.getMessage());
    }

    @Test
    void sanitizesAuthorizationInLogAttributes() {
        LokiLogAdapter.LokiResponse response = new LokiLogAdapter.LokiResponse();
        response.status = "success";
        response.data = new LokiLogAdapter.LokiResponse.Data();
        LokiLogAdapter.LokiResponse.Stream stream = new LokiLogAdapter.LokiResponse.Stream();
        stream.stream = java.util.Map.of("service_name", "checkout-api");
        long nanos = FROM.getEpochSecond() * 1_000_000_000L;
        // The authorization key should be redacted even if it slips through structured attributes
        String json = "{\"severityText\":\"DEBUG\",\"body\":\"test\","
                + "\"authorization\":\"Bearer synthetic-not-real\"}";
        stream.values = List.of(List.of(String.valueOf(nanos), json));
        response.data.result = List.of(stream);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>any());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(LokiLogAdapter.LokiResponse.class);

        LokiLogAdapter.LogAdapterResult result = adapter.fetchLogs(query());

        assertFalse(result.getLogs().isEmpty());
        LogEvidence log = result.getLogs().get(0);
        // Must not contain the raw synthetic value
        assertFalse(log.getAttributes().containsValue("Bearer synthetic-not-real"));
    }
}
