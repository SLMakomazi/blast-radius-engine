package com.madlanga.blastradius.diagnosis.mapper;

import com.madlanga.blastradius.diagnosis.dto.DiagnosisRequest;

import com.madlanga.blastradius.incident.model.EvidenceSignal;
import com.madlanga.blastradius.incident.model.IncidentAnalysis;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class DiagnosisRequestMapper {
    private final JsonMapper jsonMapper;
    private final TelemetrySanitizer sanitizer;

    public DiagnosisRequestMapper(JsonMapper jsonMapper, TelemetrySanitizer sanitizer) {
        this.jsonMapper = jsonMapper;
        this.sanitizer = sanitizer;
    }

    public DiagnosisRequest from(IncidentAnalysis analysis) {
        var coverage = analysis.coverage();
        var origin = analysis.origin();
        var severity = analysis.severity();

        var impacts = analysis.impacts().stream()
                .map(impact -> new DiagnosisRequest.Impact(
                        impact.component(),
                        impact.state().name(),
                        impact.distance(),
                        impact.path(),
                        impact.evidence().stream().map(this::evidence).toList()))
                .toList();

        return new DiagnosisRequest(
                analysis.applicationId(),
                analysis.environment(),
                analysis.from(),
                analysis.to(),
                new DiagnosisRequest.Origin(origin.component(), origin.confidence().name(), origin.evidenceScore()),
                new DiagnosisRequest.Coverage(
                        coverage.getLogs().name(),
                        coverage.getMetrics().name(),
                        coverage.getTraces().name(),
                        coverage.getHealth().name(),
                        coverage.isFullyCovered()),
                new DiagnosisRequest.Severity(severity.level().name(), severity.score(), severity.reasons()),
                impacts,
                analysis.timeline().stream().map(this::evidence).toList(),
                analysis.warnings());
    }

    public DiagnosisRequest fromStoredJson(String snapshot) {
        try {
            JsonNode root = jsonMapper.readTree(snapshot);
            JsonNode origin = required(root, "origin");
            JsonNode coverage = required(root, "coverage");
            JsonNode severity = required(root, "severity");

            return new DiagnosisRequest(
                    text(root, "applicationId"),
                    text(root, "environment"),
                    instant(root, "from"),
                    instant(root, "to"),
                    new DiagnosisRequest.Origin(
                            text(origin, "component"),
                            text(origin, "confidence"),
                            integer(origin, "evidenceScore")),
                    new DiagnosisRequest.Coverage(
                            text(coverage, "logs"),
                            text(coverage, "metrics"),
                            text(coverage, "traces"),
                            text(coverage, "health"),
                            bool(coverage, "fullyCovered")),
                    new DiagnosisRequest.Severity(
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

    private DiagnosisRequest.Evidence evidence(EvidenceSignal signal) {
        return new DiagnosisRequest.Evidence(
                signal.timestamp(), signal.component(), signal.family(), signal.signal(), signal.evidenceId());
    }

    private List<DiagnosisRequest.Impact> impacts(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DiagnosisRequest.Impact> result = new ArrayList<>();
        node.forEach(item -> result.add(new DiagnosisRequest.Impact(
                text(item, "component"),
                text(item, "state"),
                nullableInteger(item.get("distance")),
                strings(item.get("path"), false),
                evidenceList(item.get("evidence")))));
        return List.copyOf(result);
    }

    private List<DiagnosisRequest.Evidence> evidenceList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DiagnosisRequest.Evidence> result = new ArrayList<>();
        node.forEach(item -> result.add(new DiagnosisRequest.Evidence(
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
