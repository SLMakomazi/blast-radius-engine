package com.madlanga.blastradius.diagnosis.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.madlanga.blastradius.diagnosis.domain.AiDiagnosis;
import com.madlanga.blastradius.diagnosis.domain.DiagnosisContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GeminiDiagnosisAdapterTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void mapsSuccessfulGeminiResponse() throws Exception {
        AtomicInteger calls = startServer(200, 200, validResponse());

        AiDiagnosis result = adapter("test-key").diagnose(context());

        assertEquals(1, calls.get());
        assertEquals("gemini", result.provider());
        assertEquals("gemini-test", result.model());
        assertEquals("Postgres failed.", result.summary());
        assertEquals("Postgres was unavailable.", result.probableCause());
        assertEquals(List.of("Follow the approved database recovery procedure."), result.immediateActions());
    }

    @Test
    void retriesExactlyOnceAfter503AndThenSucceeds() throws Exception {
        AtomicInteger calls = startServer(503, 200, validResponse());

        AiDiagnosis result = adapter("test-key").diagnose(context());

        assertEquals(2, calls.get());
        assertEquals("gemini", result.provider());
    }

    @Test
    void failsAfterSecond503WhenNoFallbackModelsAreConfigured() throws Exception {
        AtomicInteger calls = startServer(503, 503, validResponse());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> adapter("test-key").diagnose(context()));

        assertEquals(2, calls.get());
        assertEquals("All configured Gemini models were unavailable", error.getMessage());
    }

    @Test
    void rejectsEmptyGeminiContent() throws Exception {
        AtomicInteger calls = startServer(200, 200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\"}]}}]}");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> adapter("test-key").diagnose(context()));

        assertEquals(1, calls.get());
        assertEquals("Gemini returned no diagnosis content", error.getMessage());
    }

    @Test
    void missingApiKeyFailsBeforeProviderCall() {
        GeminiProperties properties = new GeminiProperties(true, "", "gemini-test", List.of(), "http://127.0.0.1:1", 1);
        GeminiDiagnosisAdapter adapter = new GeminiDiagnosisAdapter(properties, JsonMapper.builder().build());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> adapter.diagnose(context()));

        assertEquals("Gemini API key is not configured", error.getMessage());
    }

    private GeminiDiagnosisAdapter adapter(String apiKey) {
        GeminiProperties properties = new GeminiProperties(
                true, apiKey, "gemini-test", List.of(), "http://127.0.0.1:" + server.getAddress().getPort(), 2);
        return new GeminiDiagnosisAdapter(properties, JsonMapper.builder().build());
    }

    private AtomicInteger startServer(int firstStatus, int laterStatus, String successBody) throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-test:generateContent", exchange -> {
            int call = calls.incrementAndGet();
            int status = call == 1 ? firstStatus : laterStatus;
            respond(exchange, status, status == 200 ? successBody : "{\"error\":\"temporarily unavailable\"}");
        });
        server.start();
        return calls;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String validResponse() {
        String diagnosis = """
                {"summary":"Postgres failed.","probableCause":"Postgres was unavailable.","immediateActions":["Follow the approved database recovery procedure."],"mediumTermActions":[],"strategicActions":[],"limitations":[]}
                """.trim();
        String escaped = diagnosis.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escaped + "\"}]}}]}";
    }

    private DiagnosisContext context() {
        return new DiagnosisContext(
                "document-platform",
                "local",
                Instant.parse("2026-10-03T09:40:00Z"),
                Instant.parse("2026-10-03T09:45:17Z"),
                new DiagnosisContext.Origin("postgres", "HIGH", 40),
                new DiagnosisContext.Coverage("AVAILABLE", "AVAILABLE", "AVAILABLE", "AVAILABLE", true),
                new DiagnosisContext.Severity("HIGH", 50, List.of("3 dependent components observed")),
                List.of(),
                List.of(),
                null,
                List.of());
    }
}
