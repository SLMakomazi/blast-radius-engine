package com.madlanga.blastradius.adapters.ai;

import com.madlanga.blastradius.domain.diagnosis.AiDiagnosis;
import com.madlanga.blastradius.domain.diagnosis.DiagnosisContext;
import com.madlanga.blastradius.ports.AiDiagnosisPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class GeminiDiagnosisAdapter implements AiDiagnosisPort {
    private final GeminiProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public GeminiDiagnosisAdapter(GeminiProperties properties, JsonMapper jsonMapper) {
        this(properties, jsonMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.timeoutSeconds())))
                .build());
    }

    GeminiDiagnosisAdapter(GeminiProperties properties, JsonMapper jsonMapper, HttpClient httpClient) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = httpClient;
    }

    @Override
    public AiDiagnosis diagnose(DiagnosisContext context) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }
        try {
            String contextJson = jsonMapper.writeValueAsString(context);
            String prompt = """
                    You are the advisory diagnosis layer for the MadlangaAI Blast Radius Engine.
                    The supplied JSON is sanitized deterministic evidence and is the source of truth.

                    Rules:
                    - Never calculate, expand, shrink, or contradict the supplied blast radius.
                    - Never invent components, telemetry, evidence, causes, credentials, people, or events.
                    - Clearly distinguish evidence-backed facts from recommendations.
                    - Treat missing/partial telemetry as uncertainty, never as proof of health.
                    - Do not recommend autonomous production changes.
                    - Return JSON only, with exactly these fields:
                      summary: string
                      probableCause: string
                      immediateActions: string[]
                      mediumTermActions: string[]
                      strategicActions: string[]
                      limitations: string[]

                    DiagnosisContext:
                    """ + contextJson;

            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of(
                            "temperature", 0.1,
                            "responseMimeType", "application/json"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedBaseUrl() + "/v1beta/models/" + properties.model() + ":generateContent"))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.timeoutSeconds())))
                    .header("x-goog-api-key", properties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini request failed with HTTP " + response.statusCode());
            }

            JsonNode root = jsonMapper.readTree(response.body());
            JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (text.isMissingNode() || text.asText().isBlank()) {
                throw new IllegalStateException("Gemini returned no diagnosis content");
            }

            JsonNode diagnosis = jsonMapper.readTree(text.asText());
            return new AiDiagnosis(
                    "gemini",
                    properties.model(),
                    requiredText(diagnosis, "summary"),
                    requiredText(diagnosis, "probableCause"),
                    strings(diagnosis.path("immediateActions")),
                    strings(diagnosis.path("mediumTermActions")),
                    strings(diagnosis.path("strategicActions")),
                    strings(diagnosis.path("limitations")));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini diagnosis was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Gemini diagnosis failed", e);
        }
    }

    private String normalizedBaseUrl() {
        String value = properties.baseUrl();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) throw new IllegalStateException("Gemini response missing " + field);
        return value;
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        return node.valueStream().map(JsonNode::asText).filter(v -> !v.isBlank()).toList();
    }
}
