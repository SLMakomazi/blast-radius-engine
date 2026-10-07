package com.madlanga.blastradius.diagnosis;

import com.madlanga.blastradius.incident.domain.EvidenceSignal;
import com.madlanga.blastradius.incident.domain.IncidentAnalysis;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class DiagnosisContextMapper {
    private final JsonMapper jsonMapper;
    private final TelemetrySanitizer sanitizer;

    public DiagnosisContextMapper(JsonMapper jsonMapper, TelemetrySanitizer sanitizer) {
        this.jsonMapper = jsonMapper;
        this.sanitizer = sanitizer;
    }

    public DiagnosisContext from(IncidentAnalysis analysis) {
        var coverage = analysis.coverage();
        var origin = analysis.origin();
        var severity = analysis.severity();

        var impacts = analysis.impacts().stream()
                .map(impact -> new DiagnosisContext.Impact(
                        impact.component(),
                        impact.state().name(),
                        impact.distance(),
                        impact.path(),
                        impact.evidence().stream().map(this::evidence).toList()))
                .toList();

        return new DiagnosisContext(
                analysis.applicationId(),
                analysis.environment(),
                analysis.from(),
                analysis.to(),
                new DiagnosisContext.Origin(origin.component(), origin.confidence().name(), origin.evidenceScore()),
                new DiagnosisContext.Coverage(
                        coverage.getLogs().name(),
                        coverage.getMetrics().name(),
                        coverage.getTraces().name(),
                        coverage.getHealth().name(),
                        coverage.isFullyCovered()),
                new DiagnosisContext.Severity(severity.level().name(), severity.score(), severity.reasons()),
                impacts,
                analysis.timeline().stream().map(this::evidence).toList(),
                analysis.warnings());
    }

    public DiagnosisContext fromStoredJson(String snapshot) {
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
                    strings(root.get("warnings"), true));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("stored incident snapshot is not valid diagnosis JSON", e);
        }
    }

    private DiagnosisContext.Evidence evidence(EvidenceSignal signal) {
        return new DiagnosisContext.Evidence(
                signal.timestamp(), signal.component(), signal.family(), signal.signal(), signal.evidenceId());
    }

    private List<DiagnosisContext.Impact> impacts(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
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
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DiagnosisContext.Evidence> result = new ArrayList<>();
        node.forEach(item -> result.add(new DiagnosisContext.Evidence(
                instant(item, "timestamp"),
                text(item, "component"),
                text(item, "family"),
                sanitize(text(item, "signal")),
                text(item, "evidenceId"))));
        return List.copyOf(result);
    }

    private List<String> strings(JsonNode node, boolean sanitize) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
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
        return required(parent, field).asText();
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
