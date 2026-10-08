package com.madlanga.blastradius.diagnosis.provider;

import com.madlanga.blastradius.diagnosis.dto.DiagnosisResponse;
import com.madlanga.blastradius.diagnosis.provider.DiagnosisProvider;
import com.madlanga.blastradius.diagnosis.dto.DiagnosisRequest;
import com.madlanga.blastradius.diagnosis.config.GeminiProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class GeminiDiagnosisProvider implements DiagnosisProvider {
    private static final Set<Integer> RETRYABLE_STATUS_CODES =
            Set.of(408, 429, 500, 502, 503, 504);

    private final GeminiProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public GeminiDiagnosisProvider(GeminiProperties properties, JsonMapper jsonMapper) {
        this(properties, jsonMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.timeoutSeconds())))
                .build());
    }

    GeminiDiagnosisProvider(GeminiProperties properties, JsonMapper jsonMapper, HttpClient httpClient) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = httpClient;
    }

    @Override
    public DiagnosisResponse diagnose(DiagnosisRequest context) {
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
                    - Clearly distinguish confirmed facts, hypotheses, and recommendations.
                    - A failed direct availability check confirms unavailability from the monitor, not its physical cause.
                    - Name a physical cause only with specific supporting evidence; otherwise label it undetermined.
                    - Distinguish observed request failures from potential topology impact and explain each affected path.
                    - Evidence spans the incident lifecycle; older observations are history, not necessarily current state.
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

                    DiagnosisRequest:
                    """ + contextJson;

            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of(
                            "temperature", 0.1,
                            "responseMimeType", "application/json"));
            String requestBody = jsonMapper.writeValueAsString(body);

            IllegalStateException lastModelFailure = null;
            for (String model : configuredModels()) {
                try {
                    HttpResponse<String> response = send(request(model, requestBody));
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return parseDiagnosis(response.body(), model);
                    }

                    if (response.statusCode() == 404) {
                        // A model can be unavailable, retired, or mistyped while another configured model is healthy.
                        lastModelFailure = requestFailure(response, model);
                        continue;
                    }

                    if (!RETRYABLE_STATUS_CODES.contains(response.statusCode())) {
                        // Authentication, authorization, and malformed-request failures apply to the provider/request,
                        // so trying another model would only hide the real configuration problem.
                        throw requestFailure(response, model);
                    }

                    // One bounded retry of the current model before moving to the next configured model.
                    response = send(request(model, requestBody));
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return parseDiagnosis(response.body(), model);
                    }
                    if (response.statusCode() == 404 || RETRYABLE_STATUS_CODES.contains(response.statusCode())) {
                        lastModelFailure = requestFailure(response, model);
                        continue;
                    }
                    throw requestFailure(response, model);
                } catch (HttpTimeoutException e) {
                    // A slow/unresponsive model must not prevent a configured fallback model from being attempted.
                    lastModelFailure = new IllegalStateException(
                            "Gemini request timed out for model " + model,
                            e);
                } catch (IOException e) {
                    // Transport failures may be model/request-path specific; allow the next configured model a chance.
                    lastModelFailure = new IllegalStateException(
                            "Gemini request failed for model " + model,
                            e);
                }
            }

            throw new IllegalStateException(
                    "All configured Gemini models were unavailable",
                    lastModelFailure);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini diagnosis was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Gemini diagnosis failed", e);
        }
    }

    private IllegalStateException requestFailure(HttpResponse<String> response, String model) {
        String detail = safeErrorDetail(response.body());
        String message = "Gemini request failed with HTTP " + response.statusCode() + " for model " + model;
        if (!detail.isBlank()) message += ": " + detail;
        return new IllegalStateException(message);
    }

    private String safeErrorDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "";
        try {
            JsonNode root = jsonMapper.readTree(responseBody);
            String message = root.path("error").path("message").asText();
            if (message == null || message.isBlank()) return "";
            return message.length() > 300 ? message.substring(0, 300) + "..." : message;
        } catch (Exception ignored) {
            return "";
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

    private DiagnosisResponse parseDiagnosis(String responseBody, String model) throws Exception {
        JsonNode root = jsonMapper.readTree(responseBody);
        JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (text.isMissingNode() || text.asText().isBlank()) {
            throw new IllegalStateException("Gemini returned no diagnosis content");
        }

        JsonNode diagnosis = jsonMapper.readTree(text.asText());
        return new DiagnosisResponse(
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

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
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
