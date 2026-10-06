package com.madlanga.blastradius.adapters.telemetry.tempo;

import com.madlanga.blastradius.domain.evidence.CoverageStatus;
import com.madlanga.blastradius.domain.evidence.SpanEvidence;
import com.madlanga.blastradius.domain.evidence.SpanStatus;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TempoTraceAdapterTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> uriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private TempoTraceAdapter adapter;

    private static final Instant FROM = Instant.parse("2026-10-01T10:30:00Z");
    private static final Instant TO   = Instant.parse("2026-10-01T10:35:00Z");
    private static final String TRACE_ID = "61e1c07146fcb6829b35fca26be213c3";

    @BeforeEach
    void setUp() {
        TempoProperties props = new TempoProperties();
        TelemetrySanitizer sanitizer = new TelemetrySanitizer();

        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        adapter = new TempoTraceAdapter(props, sanitizer, restClientBuilder);
    }

    private TelemetryQuery queryWithTraceId() {
        return TelemetryQuery.builder()
                .applicationId("document-platform")
                .environment("local")
                .from(FROM)
                .to(TO)
                .traceId(TRACE_ID)
                .build();
    }

    private TelemetryQuery queryWithoutTraceId() {
        return TelemetryQuery.builder()
                .applicationId("document-platform")
                .environment("local")
                .from(FROM)
                .to(TO)
                .build();
    }

    private TempoResponse buildHealthyTrace() {
        TempoResponse response = new TempoResponse();

        TempoResponse.Batch batch = new TempoResponse.Batch();
        batch.resource = new TempoResponse.Resource();
        TempoResponse.KeyValue serviceKv = new TempoResponse.KeyValue();
        serviceKv.key = "service.name";
        serviceKv.value = new TempoResponse.AnyValue();
        serviceKv.value.stringValue = "document-service";
        batch.resource.attributes = List.of(serviceKv);

        TempoResponse.ScopeSpans scope = new TempoResponse.ScopeSpans();
        TempoResponse.Span span = new TempoResponse.Span();
        span.traceId = TRACE_ID;
        span.spanId = "a08f368e31e4992c";
        span.parentSpanId = "41c503f305ad0e47";
        span.name = "POST /api/documents";
        span.kind = "SPAN_KIND_SERVER";
        span.startTimeUnixNano = String.valueOf(FROM.getEpochSecond() * 1_000_000_000L);
        span.endTimeUnixNano   = String.valueOf(FROM.getEpochSecond() * 1_000_000_000L + 1_250_000_000L);
        span.status = new TempoResponse.SpanStatusDto();
        span.status.code = "1"; // OK
        span.attributes = List.of();
        scope.spans = List.of(span);
        batch.scopeSpans = List.of(scope);
        response.batches = List.of(batch);
        return response;
    }

    @Test
    void returnsUnavailableWhenTempoIsUnreachable() {
        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doThrow(new ResourceAccessException("Connection refused"))
                .when(responseSpec).body(TempoResponse.class);

        TempoTraceAdapter.TraceAdapterResult result = adapter.fetchSpans(queryWithTraceId());

        assertEquals(CoverageStatus.UNAVAILABLE, result.getCoverage());
        assertTrue(result.getSpans().isEmpty());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void mapsOtlpBatchToNormalizedSpanEvidence() {
        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(buildHealthyTrace()).when(responseSpec).body(TempoResponse.class);

        TempoTraceAdapter.TraceAdapterResult result = adapter.fetchSpans(queryWithTraceId());

        assertEquals(CoverageStatus.AVAILABLE, result.getCoverage());
        assertEquals(1, result.getSpans().size());

        SpanEvidence span = result.getSpans().get(0);
        assertEquals(TRACE_ID, span.getTraceId());
        assertEquals("a08f368e31e4992c", span.getSpanId());
        assertEquals("41c503f305ad0e47", span.getParentSpanId());
        assertEquals("document-service", span.getService());
        assertEquals("POST /api/documents", span.getOperation());
        assertEquals(SpanStatus.OK, span.getStatus());
        assertEquals(1250, span.getDurationMs());
        assertFalse(span.isRootSpan());
        assertEquals("local-tempo", span.getProvenance().getProvider());
    }

    @Test
    void preservesTraceIdAndSpanId() {
        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(buildHealthyTrace()).when(responseSpec).body(TempoResponse.class);

        TempoTraceAdapter.TraceAdapterResult result = adapter.fetchSpans(queryWithTraceId());

        assertFalse(result.getSpans().isEmpty());
        SpanEvidence span = result.getSpans().get(0);
        assertEquals(TRACE_ID, span.getTraceId());
        assertEquals("a08f368e31e4992c", span.getSpanId());
    }


    @Test
    void normalizesRealTempoBase64IdsToHexForCrossProviderCorrelation() {
        TempoResponse response = buildHealthyTrace();
        TempoResponse.Span span = response.batches.get(0).scopeSpans.get(0).spans.get(0);

        // Real Tempo /api/traces payload shape observed in the local lab.
        span.traceId = "AWV2g0KeagpBHAjBqFVERA==";
        span.spanId = "JoH0pF5bLyk=";
        span.parentSpanId = "KpLLlwgkSO8=";

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(TempoResponse.class);

        TempoTraceAdapter.TraceAdapterResult result = adapter.fetchSpans(queryWithTraceId());

        assertEquals(CoverageStatus.AVAILABLE, result.getCoverage());
        SpanEvidence normalized = result.getSpans().get(0);
        assertEquals("01657683429e6a0a411c08c1a8554444", normalized.getTraceId());
        assertEquals("2681f4a45e5b2f29", normalized.getSpanId());
        assertEquals("2a92cb97082448ef", normalized.getParentSpanId());
    }

    @Test
    void preservesAlreadyHexEncodedOtlpIds() {
        assertEquals("61e1c07146fcb6829b35fca26be213c3",
                TempoTraceAdapter.normalizeOtlpId("61E1C07146FCB6829B35FCA26BE213C3"));
        assertEquals("a08f368e31e4992c",
                TempoTraceAdapter.normalizeOtlpId("a08f368e31e4992c"));
        assertNull(TempoTraceAdapter.normalizeOtlpId(null));
    }

    @Test
    void mapsErrorSpanCorrectly() {
        TempoResponse response = buildHealthyTrace();
        TempoResponse.Span errorSpan = response.batches.get(0).scopeSpans.get(0).spans.get(0);
        errorSpan.status.code = "2"; // ERROR
        TempoResponse.KeyValue errorKv = new TempoResponse.KeyValue();
        errorKv.key = "error.type";
        errorKv.value = new TempoResponse.AnyValue();
        errorKv.value.stringValue = "ConnectionException";
        errorSpan.attributes = List.of(errorKv);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(TempoResponse.class);

        TempoTraceAdapter.TraceAdapterResult result = adapter.fetchSpans(queryWithTraceId());

        assertFalse(result.getSpans().isEmpty());
        SpanEvidence span = result.getSpans().get(0);
        assertTrue(span.isError());
        assertEquals("ConnectionException", span.getErrorType());
    }

    @Test
    void mapsRealTempoSymbolicErrorStatus() {
        TempoResponse response = buildHealthyTrace();
        TempoResponse.Span errorSpan = response.batches.get(0).scopeSpans.get(0).spans.get(0);
        errorSpan.status.code = "STATUS_CODE_ERROR";

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(TempoResponse.class);

        TempoTraceAdapter.TraceAdapterResult result = adapter.fetchSpans(queryWithTraceId());

        assertEquals(CoverageStatus.AVAILABLE, result.getCoverage());
        assertTrue(result.getSpans().get(0).isError());
    }

    @Test
    void doesNotTreatServerAddressOnServerSpanAsDependencyPeer() {
        TempoResponse response = buildHealthyTrace();
        TempoResponse.Span span = response.batches.get(0).scopeSpans.get(0).spans.get(0);
        span.kind = "SPAN_KIND_SERVER";

        TempoResponse.KeyValue address = new TempoResponse.KeyValue();
        address.key = "server.address";
        address.value = new TempoResponse.AnyValue();
        address.value.stringValue = "localhost";
        span.attributes = List.of(address);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(TempoResponse.class);

        TempoTraceAdapter.TraceAdapterResult result = adapter.fetchSpans(queryWithTraceId());

        assertEquals(CoverageStatus.AVAILABLE, result.getCoverage());
        assertNull(result.getSpans().get(0).getPeerService());
    }

    @Test
    void doesNotInventTechnologyIdentityForLoopbackDatabase() {
        TempoResponse response = buildHealthyTrace();
        TempoResponse.Span span = response.batches.get(0).scopeSpans.get(0).spans.get(0);
        span.kind = "SPAN_KIND_CLIENT";

        TempoResponse.KeyValue db = new TempoResponse.KeyValue();
        db.key = "db.system.name";
        db.value = new TempoResponse.AnyValue();
        db.value.stringValue = "postgresql";
        TempoResponse.KeyValue address = new TempoResponse.KeyValue();
        address.key = "server.address";
        address.value = new TempoResponse.AnyValue();
        address.value.stringValue = "localhost";
        span.attributes = List.of(db, address);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(TempoResponse.class);

        TempoTraceAdapter.TraceAdapterResult result = adapter.fetchSpans(queryWithTraceId());

        assertNull(result.getSpans().get(0).getPeerService());
        assertEquals("postgresql", result.getSpans().get(0).getAttributes().get("db.system.name"));
    }

    @Test
    void usesConcreteDatabaseEndpointAsPeerAndKeepsTechnologyInAttributes() {
        TempoResponse response = buildHealthyTrace();
        TempoResponse.Span span = response.batches.get(0).scopeSpans.get(0).spans.get(0);
        span.kind = "SPAN_KIND_INTERNAL";

        TempoResponse.KeyValue db = new TempoResponse.KeyValue();
        db.key = "db.system.name";
        db.value = new TempoResponse.AnyValue();
        db.value.stringValue = "postgresql";
        TempoResponse.KeyValue address = new TempoResponse.KeyValue();
        address.key = "server.address";
        address.value = new TempoResponse.AnyValue();
        address.value.stringValue = "postgres";
        span.attributes = List.of(db, address);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(TempoResponse.class);

        TempoTraceAdapter.TraceAdapterResult result = adapter.fetchSpans(queryWithTraceId());

        assertEquals("postgres", result.getSpans().get(0).getPeerService());
        assertEquals("postgresql", result.getSpans().get(0).getAttributes().get("db.system.name"));
    }

    @Test
    void retriesTransientTraceFetchFailureAfterSuccessfulSearch() {
        TempoSearchResponse search = new TempoSearchResponse();
        TempoSearchResponse.TraceSummary summary = new TempoSearchResponse.TraceSummary();
        summary.traceId = TRACE_ID;
        summary.startTimeUnixNano = String.valueOf(FROM.getEpochSecond() * 1_000_000_000L);
        search.traces = List.of(summary);

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(ArgumentMatchers.any(java.util.function.Function.class));
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        when(responseSpec.body(TempoSearchResponse.class)).thenReturn(search);
        when(responseSpec.body(TempoResponse.class))
                .thenThrow(new ResourceAccessException("transient Tempo read failure"))
                .thenReturn(buildHealthyTrace());

        TempoTraceAdapter.TraceAdapterResult result = adapter.fetchSpans(queryWithoutTraceId());

        assertEquals(CoverageStatus.AVAILABLE, result.getCoverage());
        assertEquals(1, result.getSpans().size());
        assertTrue(result.getWarnings().isEmpty());
        verify(responseSpec, times(2)).body(TempoResponse.class);
    }

    @Test
    void filtersMixedTraceBySpanStartWithInclusiveFromAndExclusiveTo() {
        TempoResponse response = buildHealthyTrace();
        TempoResponse.Span inside = response.batches.get(0).scopeSpans.get(0).spans.get(0);
        TempoResponse.Span old = buildHealthyTrace().batches.get(0).scopeSpans.get(0).spans.get(0);
        old.startTimeUnixNano = String.valueOf(FROM.minusSeconds(60).getEpochSecond() * 1_000_000_000L);
        old.status.code = "STATUS_CODE_ERROR";
        TempoResponse.Span atEnd = buildHealthyTrace().batches.get(0).scopeSpans.get(0).spans.get(0);
        atEnd.startTimeUnixNano = String.valueOf(TO.getEpochSecond() * 1_000_000_000L);
        atEnd.status.code = "STATUS_CODE_ERROR";
        response.batches.get(0).scopeSpans.get(0).spans = List.of(old, inside, atEnd);
        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(TempoResponse.class);
        var result = adapter.fetchSpans(queryWithTraceId());
        assertEquals(CoverageStatus.AVAILABLE, result.getCoverage());
        assertEquals(1, result.getSpans().size());
        assertEquals(FROM, result.getSpans().getFirst().getStartTime());
        assertFalse(result.getSpans().getFirst().isError());
    }

    @Test
    void returnsUnavailableWhenResponseHasNoBatches() {
        TempoResponse empty = new TempoResponse();
        empty.batches = List.of();

        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), anyString());
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(empty).when(responseSpec).body(TempoResponse.class);

        TempoTraceAdapter.TraceAdapterResult result = adapter.fetchSpans(queryWithTraceId());

        assertEquals(CoverageStatus.UNAVAILABLE, result.getCoverage());
    }
}
