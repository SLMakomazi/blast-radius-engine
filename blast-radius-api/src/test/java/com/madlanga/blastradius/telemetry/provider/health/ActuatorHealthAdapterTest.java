package com.madlanga.blastradius.telemetry.provider.health;

import com.madlanga.blastradius.telemetry.config.TelemetryConfig;

import com.madlanga.blastradius.telemetry.model.CoverageStatus;
import com.madlanga.blastradius.telemetry.model.HealthEvidence;
import com.madlanga.blastradius.telemetry.model.HealthState;
import com.madlanga.blastradius.telemetry.model.TelemetryQuery;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActuatorHealthAdapterTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> uriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private ActuatorHealthAdapter adapter;

    private static final Instant FROM = Instant.parse("2026-10-01T10:30:00Z");
    private static final Instant TO   = Instant.parse("2026-10-01T10:35:00Z");

    @BeforeEach
    void setUp() {
        TelemetryConfig.ActuatorHealthProperties props = new TelemetryConfig.ActuatorHealthProperties();
        // Single component for deterministic testing
        props.setEndpoints(Map.of("document-service", "http://document-service:8083"));

        TelemetrySanitizer sanitizer = new TelemetrySanitizer();

        when(restClientBuilder.build()).thenReturn(restClient);

        adapter = new ActuatorHealthAdapter(props, sanitizer, restClientBuilder);
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
    void mapsUpResponseToUpHealthState() {
        ActuatorHealthAdapter.ActuatorHealthResponse response = new ActuatorHealthAdapter.ActuatorHealthResponse();
        response.status = "UP";

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
        doReturn(response).when(responseSpec).body(ActuatorHealthAdapter.ActuatorHealthResponse.class);

        ActuatorHealthAdapter.HealthAdapterResult result = adapter.fetchHealth(query());

        assertEquals(CoverageStatus.AVAILABLE, result.getCoverage());
        List<HealthEvidence> healthList = result.getHealth().stream()
                .filter(h -> "/actuator/health".equals(h.getEndpoint()))
                .toList();
        assertFalse(healthList.isEmpty());
        assertTrue(healthList.stream().anyMatch(h -> h.getState() == HealthState.UP));
    }

    @Test
    void unreachableComponentReportsUnknownNotHealthy() {
        // A DOWN/UNKNOWN health result is still EVIDENCE — not silence
        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
        doThrow(new ResourceAccessException("Connection refused"))
                .when(responseSpec).body(ActuatorHealthAdapter.ActuatorHealthResponse.class);

        ActuatorHealthAdapter.HealthAdapterResult result = adapter.fetchHealth(query());

        // Coverage is PARTIAL (some endpoints probed, some failed)
        // or UNAVAILABLE if none reached — still not fabricated as UP
        assertNotNull(result);

        // Every evidence item must exist — unreachable means UNKNOWN, not missing
        boolean hasUnknownOrDown = result.getHealth().stream()
                .anyMatch(h -> h.getState() == HealthState.UNKNOWN || h.getState() == HealthState.DOWN);
        assertTrue(hasUnknownOrDown,
                "Unreachable component must produce UNKNOWN evidence, not be silently dropped");

        // There must be a warning explaining the failure
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void downReadinessIsRealEvidence() {
        // A 503 response from /actuator/health/readiness must produce DOWN evidence
        // This is the PostgreSQL-failed scenario
        ActuatorHealthAdapter.ActuatorHealthResponse downResponse = new ActuatorHealthAdapter.ActuatorHealthResponse();
        downResponse.status = "DOWN";
        ActuatorHealthAdapter.ActuatorHealthResponse.ComponentHealth dbHealth = new ActuatorHealthAdapter.ActuatorHealthResponse.ComponentHealth();
        dbHealth.status = "DOWN";
        downResponse.components = Map.of("db", dbHealth);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
        doReturn(downResponse).when(responseSpec).body(ActuatorHealthAdapter.ActuatorHealthResponse.class);

        ActuatorHealthAdapter.HealthAdapterResult result = adapter.fetchHealth(query());

        boolean hasDownEvidence = result.getHealth().stream()
                .anyMatch(h -> h.getState() == HealthState.DOWN);
        assertTrue(hasDownEvidence, "DOWN response must produce DOWN health evidence");
    }

    @Test
    void preservesTimestamp() {
        ActuatorHealthAdapter.ActuatorHealthResponse response = new ActuatorHealthAdapter.ActuatorHealthResponse();
        response.status = "UP";

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
        doReturn(response).when(responseSpec).body(ActuatorHealthAdapter.ActuatorHealthResponse.class);

        ActuatorHealthAdapter.HealthAdapterResult result = adapter.fetchHealth(query());

        assertFalse(result.getHealth().isEmpty());
        // All evidence items must have a non-null timestamp
        result.getHealth().forEach(h -> assertNotNull(h.getTimestamp()));
    }

    @Test
    void componentFilterLimitsProbes() {
        // With a filter that excludes document-service, no probes should fire
        TelemetryQuery filtered = TelemetryQuery.builder()
                .applicationId("document-platform")
                .environment("local")
                .from(FROM)
                .to(TO)
                .componentFilter(List.of("payment-service")) // not document-service
                .build();

        ActuatorHealthAdapter.HealthAdapterResult result = adapter.fetchHealth(filtered);

        assertEquals(CoverageStatus.UNAVAILABLE, result.getCoverage());
        assertTrue(result.getHealth().isEmpty());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void provenanceIdentifiesProvider() {
        ActuatorHealthAdapter.ActuatorHealthResponse response = new ActuatorHealthAdapter.ActuatorHealthResponse();
        response.status = "UP";

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
        doReturn(response).when(responseSpec).body(ActuatorHealthAdapter.ActuatorHealthResponse.class);

        ActuatorHealthAdapter.HealthAdapterResult result = adapter.fetchHealth(query());

        assertFalse(result.getHealth().isEmpty());
        result.getHealth().forEach(h ->
                assertEquals("local-actuator", h.getProvenance().getProvider()));
    }
}
