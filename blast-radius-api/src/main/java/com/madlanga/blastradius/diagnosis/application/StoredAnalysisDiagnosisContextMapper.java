package com.madlanga.blastradius.diagnosis.application;

import com.madlanga.blastradius.diagnosis.domain.DiagnosisContext;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds diagnosis context from a stored analysis snapshot.
 * Redacts free text again before it can reach the AI provider.
 */
public final class StoredAnalysisDiagnosisContextMapper {
    private final JsonMapper jsonMapper;
    private final TelemetrySanitizer sanitizer;

    public StoredAnalysisDiagnosisContextMapper(JsonMapper jsonMapper, TelemetrySanitizer sanitizer) {
        this.jsonMapper = jsonMapper;
        this.sanitizer = sanitizer;
    }

    public DiagnosisContext fromJson(String snapshot) {
        try {
            JsonNode root = jsonMapper.readTree(snapshot);
            JsonNode origin = required(root, "origin");
            JsonNode coverage = required(root, "coverage");
            JsonNode severity = required(root, "severity");

            return new DiagnosisContext(
                    text(root, "applicationId"),
                    text(root, "environment"),
                    instant(root, "from"),
                    instant(root, "to"),
                    new DiagnosisContext.Origin(
                            text(origin, "component"),
                            text(origin, "confidence"),
                            integer(origin, "evidenceScore")),
                    new DiagnosisContext.Coverage(
                            text(coverage, "logs"),
                            text(coverage, "metrics"),
                            text(coverage, "traces"),
                            text(coverage, "health"),
                            bool(coverage, "fullyCovered")),
                    new DiagnosisContext.Severity(
                            text(severity, "level"),
                            integer(severity, "score"),
                            strings(severity.get("reasons"), true)),
                    impacts(root.get("impacts")),
                    evidenceList(root.get("timeline")),
                    experiment(root.get("experimentAssessment")),
                    strings(root.get("warnings"), true));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("stored incident snapshot is not valid diagnosis JSON", e);
        }
    }

    private List<DiagnosisContext.Impact> impacts(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<DiagnosisContext.Impact> result = new ArrayList<>();
        node.forEach(item -> result.add(new DiagnosisContext.Impact(
                text(item, "component"),
                text(item, "state"),
                nullableInteger(item.get("distance")),
                strings(item.get("path"), false),
                evidenceList(item.get("evidence")))));
        return List.copyOf(result);
    }

    private List<DiagnosisContext.Evidence> evidenceList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<DiagnosisContext.Evidence> result = new ArrayList<>();
        node.forEach(item -> result.add(new DiagnosisContext.Evidence(
                instant(item, "timestamp"),
                text(item, "component"),
                text(item, "family"),
                sanitize(text(item, "signal")),
                text(item, "evidenceId"))));
        return List.copyOf(result);
    }

    private DiagnosisContext.Experiment experiment(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return new DiagnosisContext.Experiment(
                text(node, "experimentId"),
                text(node, "containment"),
                strings(node.get("expectedImpact"), false),
                strings(node.get("observedExpectedImpact"), false),
                strings(node.get("expectedButUnobserved"), false),
                strings(node.get("unexpectedImpact"), false));
    }

    private List<String> strings(JsonNode node, boolean sanitize) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText();
            values.add(sanitize ? sanitize(value) : value);
        });
        return List.copyOf(values);
    }

    private String sanitize(String value) {
        return sanitizer.sanitizeMessage(value);
    }

    private JsonNode required(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("stored incident snapshot is missing required field: " + field);
        }
        return value;
    }

    private String text(JsonNode parent, String field) {
        JsonNode value = required(parent, field);
        return value.asText();
    }

    private int integer(JsonNode parent, String field) {
        return required(parent, field).asInt();
    }

    private Integer nullableInteger(JsonNode value) {
        return value == null || value.isNull() ? null : value.asInt();
    }

    private boolean bool(JsonNode parent, String field) {
        return required(parent, field).asBoolean();
    }

    private Instant instant(JsonNode parent, String field) {
        return Instant.parse(text(parent, field));
    }
}
