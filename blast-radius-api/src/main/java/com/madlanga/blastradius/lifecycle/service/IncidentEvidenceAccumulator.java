package com.madlanga.blastradius.lifecycle.service;

import com.madlanga.blastradius.incident.model.IncidentAnalysis;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Append-only incident history; caps stop collection explicitly, never evict stored observations. */
public final class IncidentEvidenceAccumulator {
    private final JsonMapper mapper;
    public IncidentEvidenceAccumulator(JsonMapper mapper) { this.mapper = mapper; }

    public String merge(String previous, IncidentAnalysis analysis, int limit, Duration collectionDuration) {
        ObjectNode next = (ObjectNode) mapper.valueToTree(analysis);
        ObjectNode old = previous == null ? mapper.createObjectNode() : (ObjectNode) mapper.readTree(previous);
        LinkedHashMap<String, JsonNode> evidence = new LinkedHashMap<>();
        old.path("timeline").forEach(e -> evidence.put(key(e), e));
        Instant firstCollection = old.has("collectionStartedAt")
                ? Instant.parse(old.get("collectionStartedAt").asText()) : Instant.now();
        boolean durationExceeded = Instant.now().isAfter(firstCollection.plus(collectionDuration));
        boolean limited = old.path("collectionLimited").asBoolean(false);
        for (JsonNode e : next.path("timeline")) {
            if (evidence.containsKey(key(e))) continue;
            if (durationExceeded || evidence.size() >= limit) { limited = true; continue; }
            evidence.put(key(e), e);
        }
        var timeline = mapper.createArrayNode();
        evidence.values().stream().sorted(Comparator.comparing(e -> e.path("timestamp").asText())).forEach(timeline::add);
        next.set("timeline", timeline);
        LinkedHashMap<String, ObjectNode> impacts = new LinkedHashMap<>();
        old.path("impacts").forEach(i -> impacts.put(i.path("component").asText(), (ObjectNode) i.deepCopy()));
        next.path("impacts").forEach(i -> {
            String component = i.path("component").asText();
            ObjectNode prior = impacts.get(component);
            ObjectNode merged = (ObjectNode) i.deepCopy();
            if (prior != null && Set.of("OBSERVED", "UNEXPECTED").contains(prior.path("state").asText()))
                merged.set("state", prior.get("state"));
            impacts.put(component, merged);
        });
        var impactArray = mapper.createArrayNode();
        impacts.forEach((component, impact) -> {
            var items = mapper.createArrayNode();
            evidence.values().stream().filter(e -> component.equals(e.path("component").asText())
                    && !"AVAILABILITY_AVAILABLE".equals(e.path("kind").asText())).forEach(items::add);
            impact.set("evidence", items);
            impactArray.add(impact);
        });
        next.set("impacts", impactArray);
        String origin = analysis.origin().component();
        if (old.path("origin").has("confidence")) ((ObjectNode) next.get("origin")).set("confidence", old.path("origin").get("confidence"));
        impacts.values().stream().filter(i -> origin.equals(i.path("component").asText())).findFirst()
                .ifPresent(i -> ((ObjectNode) next.get("origin")).set("evidence", i.get("evidence")));
        if (old.has("from") && old.get("from").asText().compareTo(next.get("from").asText()) < 0) next.set("from", old.get("from"));
        if (old.has("to") && old.get("to").asText().compareTo(next.get("to").asText()) > 0) next.set("to", old.get("to"));
        if (old.path("severity").path("score").asInt() > next.path("severity").path("score").asInt()) next.set("severity", old.get("severity"));
        var warnings = new LinkedHashSet<String>();
        if (old.has("to") && analysis.from().isAfter(Instant.parse(old.get("to").asText())))
            warnings.add("Evidence collection gap after " + old.get("to").asText() + ": monitoring resumed with a later telemetry window; retained history is unchanged.");
        old.path("warnings").forEach(w -> warnings.add(w.asText()));
        next.path("warnings").forEach(w -> warnings.add(w.asText()));
        if (limited) warnings.add("Incident evidence collection limit reached. Previously collected history is retained; subsequent observations may be absent. Availability monitoring and guarded recovery continue.");
        next.set("warnings", mapper.valueToTree(warnings));
        boolean confirmed = analysis.origin().evidence().stream().anyMatch(e -> e.confirmsUnavailable())
                || "OUTAGE".equals(old.path("incidentType").asText());
        next.put("incidentType", confirmed ? "OUTAGE" : "UNCLASSIFIED");
        next.put("availabilityStatus", confirmed ? "UNAVAILABLE" : "UNKNOWN");
        next.put("availabilityExplanation", confirmed ? "Confirmed unavailable — direct availability check failed." : "No retained direct availability confirmation.");
        next.put("rootCause", "UNDETERMINED");
        next.put("evidenceVersion", old.path("evidenceVersion").asLong(0) + 1);
        next.put("collectionStartedAt", firstCollection.toString());
        next.put("collectionLimited", limited);
        next.put("lastCollectedAt", Instant.now().toString());
        return mapper.writeValueAsString(next);
    }

    // Provider adapters sometimes assign a new UUID each fetch. IDs alone cannot deduplicate overlapping windows.
    static String key(JsonNode e) {
        String id = e.path("evidenceId").asText();
        if (id.startsWith("stable-")) {
            String category = e.path("family").asText().equals("METRIC")
                    ? e.path("signal").asText().replaceAll("[0-9]+(?:\\.[0-9]+)?", "#") : e.path("signal").asText();
            return id + "\u0000" + e.path("component").asText() + "\u0000" + category;
        }
        return String.join("\u0000", e.path("timestamp").asText(), e.path("component").asText(),
                e.path("family").asText(), e.path("provider").asText(), e.path("sourceRef").asText(), e.path("signal").asText());
    }
}
