package com.madlanga.blastradius.telemetry.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpanEvidenceTest {

    private static final Instant START = Instant.parse("2026-10-01T10:31:02Z");
    private static final String TRACE_ID = "61e1c07146fcb6829b35fca26be213c3";
    private static final String SPAN_ID  = "a08f368e31e4992c";
    private static final String PARENT   = "41c503f305ad0e47";

    private EvidenceProvenance provenance() {
        return EvidenceProvenance.of(EvidenceFamily.TRACES, "local-tempo",
                Instant.now(), "trace/" + TRACE_ID);
    }

    @Test
    void buildsWithRequiredFields() {
        SpanEvidence s = SpanEvidence.builder()
                .id("span-001")
                .traceId(TRACE_ID)
                .spanId(SPAN_ID)
                .service("document-service")
                .startTime(START)
                .provenance(provenance())
                .build();

        assertEquals(TRACE_ID, s.getTraceId());
        assertEquals(SPAN_ID, s.getSpanId());
        assertEquals("document-service", s.getService());
        assertEquals(SpanStatus.UNSET, s.getStatus()); // default
    }

    @Test
    void preservesTraceAndSpanIds() {
        SpanEvidence s = SpanEvidence.builder()
                .id("span-002")
                .traceId(TRACE_ID)
                .spanId(SPAN_ID)
                .parentSpanId(PARENT)
                .service("document-service")
                .startTime(START)
                .provenance(provenance())
                .build();

        assertEquals(TRACE_ID, s.getTraceId());
        assertEquals(SPAN_ID, s.getSpanId());
        assertEquals(PARENT, s.getParentSpanId());
        assertFalse(s.isRootSpan());
    }

    @Test
    void identifiesRootSpan() {
        SpanEvidence s = SpanEvidence.builder()
                .id("span-root")
                .traceId(TRACE_ID)
                .spanId("4a627d450e97f6bf")
                .service("payment-service")
                .startTime(START)
                .provenance(provenance())
                .build();

        assertTrue(s.isRootSpan());
    }

    @Test
    void preservesTimestamp() {
        Instant specific = Instant.parse("2026-10-01T10:31:02.500Z");
        SpanEvidence s = SpanEvidence.builder()
                .id("span-003")
                .traceId(TRACE_ID)
                .spanId(SPAN_ID)
                .service("document-service")
                .startTime(specific)
                .provenance(provenance())
                .build();
        assertEquals(specific, s.getStartTime());
    }

    @Test
    void preservesDuration() {
        SpanEvidence s = SpanEvidence.builder()
                .id("span-004")
                .traceId(TRACE_ID)
                .spanId(SPAN_ID)
                .service("document-service")
                .startTime(START)
                .durationMs(1250)
                .provenance(provenance())
                .build();
        assertEquals(1250, s.getDurationMs());
    }

    @Test
    void errorSpanPreservesErrorType() {
        SpanEvidence s = SpanEvidence.builder()
                .id("span-005")
                .traceId(TRACE_ID)
                .spanId(SPAN_ID)
                .parentSpanId(PARENT)
                .service("document-service")
                .startTime(START)
                .status(SpanStatus.ERROR)
                .errorType("ConnectionException")
                .provenance(provenance())
                .build();

        assertTrue(s.isError());
        assertEquals("ConnectionException", s.getErrorType());
    }

    @Test
    void representsJdbcSpan() {
        SpanEvidence s = SpanEvidence.builder()
                .id("span-jdbc")
                .traceId(TRACE_ID)
                .spanId("069c7fb20b8ab97f")
                .parentSpanId(SPAN_ID)
                .service("document-service")
                .operation("INSERT synthetic_lab.synthetic_documents")
                .startTime(START)
                .durationMs(45)
                .status(SpanStatus.OK)
                .peerService("postgresql")
                .attributes(Map.of("db.system", "postgresql", "db.operation", "INSERT"))
                .provenance(provenance())
                .build();

        assertEquals("postgresql", s.getPeerService());
        assertEquals("INSERT synthetic_lab.synthetic_documents", s.getOperation());
    }

    @Test
    void crossServicePropagation() {
        // payment → customer → document
        // Verify parent chain can be represented
        SpanEvidence payment = SpanEvidence.builder()
                .id("span-pay")
                .traceId(TRACE_ID)
                .spanId("4a627d450e97f6bf")
                .service("payment-service")
                .operation("POST /api/payments")
                .startTime(START)
                .provenance(provenance())
                .build();

        SpanEvidence customer = SpanEvidence.builder()
                .id("span-cust")
                .traceId(TRACE_ID)
                .spanId("58aab8c1d21212c6")
                .parentSpanId(payment.getSpanId())
                .service("customer-service")
                .operation("POST /api/customers/validate")
                .startTime(START.plusMillis(5))
                .provenance(provenance())
                .build();

        assertEquals(payment.getSpanId(), customer.getParentSpanId());
        assertEquals(payment.getTraceId(), customer.getTraceId());
    }

    @Test
    void attributesAreImmutable() {
        SpanEvidence s = SpanEvidence.builder()
                .id("span-006")
                .traceId(TRACE_ID)
                .spanId(SPAN_ID)
                .service("document-service")
                .startTime(START)
                .provenance(provenance())
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> s.getAttributes().put("key", "value"));
    }
}
