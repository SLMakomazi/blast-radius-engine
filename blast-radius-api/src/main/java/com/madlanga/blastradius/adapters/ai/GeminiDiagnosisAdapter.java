package com.madlanga.blastradius.adapters.ai;

import com.madlanga.blastradius.domain.diagnosis.AiDiagnosis;
import com.madlanga.blastradius.domain.diagnosis.DiagnosisContext;
import com.madlanga.blastradius.ports.AiDiagnosisPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class GeminiDiagnosisAdapter implements AiDiagnosisPort {
    private static final java.util.Set<Integer> RETRYABLE_STATUS_CODES =
            java.util.Set.of(408, 429, 500, 502, 503, 504);

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
                    - Evidence observed in a component does not prove that component caused the incident.
                    - Keep the deterministic origin separate from the services where its effects were observed.
                    - In recommendations, deal with the incident origin first. Put resilience and containment improvements after the immediate fix.
                    - Treat missing/partial telemetry as uncertainty, never as proof of health.
                    - Do not recommend autonomous production changes.
                    - Never tell an operator to restart, fail over, roll back, or change production infrastructure directly. If recovery may require such an action, say to confirm the condition and follow the approved recovery procedure.
                    - Write for engineers, support teams, managers, and non-technical readers using simple, clear English.
                    - Prefer short sentences and common words. Explain technical terms briefly when they are needed.
                    - Avoid formal or academic wording such as "manifested", "propagating", "cascading", "architect", or "remediation" when a simpler phrase works.
                    - Keep the technical facts, service names, metric names, error names, and evidence exact even when simplifying the explanation.
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
            String requestBody = jsonMapper.writeValueAsString(body);

            IllegalStateException lastTransientFailure = null;
            for (String model : configuredModels()) {
                HttpResponse<String> response = send(request(model, requestBody));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseDiagnosis(response.body(), model);
                }

                if (!RETRYABLE_STATUS_CODES.contains(response.statusCode())) {
                    throw new IllegalStateException(
                            "Gemini request failed with HTTP " + response.statusCode() + " for model " + model);
                }

                // One bounded retry of the current model before moving to the next configured model.
                response = send(request(model, requestBody));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseDiagnosis(response.body(), model);
                }
                if (!RETRYABLE_STATUS_CODES.contains(response.statusCode())) {
                    throw new IllegalStateException(
                            "Gemini request failed with HTTP " + response.statusCode() + " for model " + model);
                }
                lastTransientFailure = new IllegalStateException(
                        "Gemini request failed with HTTP " + response.statusCode() + " for model " + model);
            }

            throw new IllegalStateException(
                    "All configured Gemini models were unavailable",
                    lastTransientFailure);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini diagnosis was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Gemini diagnosis failed", e);
        }
    }

    private HttpRequest request(String model, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(normalizedBaseUrl() + "/v1beta/models/" + model + ":generateContent"))
                .timeout(Duration.ofSeconds(Math.max(1, properties.timeoutSeconds())))
                .header("x-goog-api-key", properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private AiDiagnosis parseDiagnosis(String responseBody, String model) throws Exception {
        JsonNode root = jsonMapper.readTree(responseBody);
        JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (text.isMissingNode() || text.asText().isBlank()) {
            throw new IllegalStateException("Gemini returned no diagnosis content");
        }

        JsonNode diagnosis = jsonMapper.readTree(text.asText());
        return new AiDiagnosis(
                "gemini",
                model,
                requiredText(diagnosis, "summary"),
                requiredText(diagnosis, "probableCause"),
                strings(diagnosis.path("immediateActions")),
                strings(diagnosis.path("mediumTermActions")),
                strings(diagnosis.path("strategicActions")),
                strings(diagnosis.path("limitations")));
    }

    private List<String> configuredModels() {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        addModel(models, properties.model());
        properties.fallbackModels().forEach(model -> addModel(models, model));
        if (models.isEmpty()) throw new IllegalStateException("No Gemini model is configured");
        return List.copyOf(models);
    }

    private void addModel(LinkedHashSet<String> models, String model) {
        if (model != null && !model.isBlank()) models.add(model.trim());
    }

    private HttpResponse<String> send(HttpRequest request) throws java.io.IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
