package com.madlanga.blastradius.lifecycle.service;

import com.madlanga.blastradius.incident.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class IncidentEvidenceAccumulatorTest {
    final JsonMapper mapper = JsonMapper.builder().build();
    final IncidentEvidenceAccumulator accumulator = new IncidentEvidenceAccumulator(mapper);
    final Instant start = Instant.parse("2026-10-08T01:00:00Z");
    EvidenceSignal signal(int second, String id) {
        return new EvidenceSignal(start.plusSeconds(second), "ledger-db", "HEALTH", "availability health DOWN", id,
                EvidenceSignal.Kind.AVAILABILITY_UNAVAILABLE, "direct-jdbc", "database/availability", start.plusSeconds(second+1));
    }
    IncidentAnalysis analysis(EvidenceSignal... evidence) {
        var items = List.of(evidence);
        return new IncidentAnalysis("ledger", "test", start, start.plusSeconds(60),
                new OriginAssessment("ledger-db", OriginAssessment.Confidence.HIGH, 50, items), null,
                List.of(new ComponentImpact("ledger-db", ComponentImpact.State.ORIGIN, 0, List.of("ledger-db"), items)),
                items, new IncidentSeverity(IncidentSeverity.Level.HIGH,50,List.of()),List.of());
    }
    @Test void overlappingWindowsAccumulateAndDeduplicateUnstableProviderIds() {
        String first = accumulator.merge(null, analysis(signal(1,"random-a")), 100, Duration.ofDays(1));
        String second = accumulator.merge(first, analysis(signal(1,"random-b"),signal(30,"new")),100,Duration.ofDays(1));
        var result = mapper.readTree(second);
        assertThat(result.path("timeline").size()).isEqualTo(2);
        assertThat(result.path("evidenceVersion").asLong()).isEqualTo(2);
        assertThat(result.path("timeline").get(0).path("evidenceId").asText()).isEqualTo("random-a");
        assertThat(result.path("timeline").get(0).path("collectedAt").asText()).isEqualTo(start.plusSeconds(2).toString());
        assertThat(result.path("incidentType").asText()).isEqualTo("OUTAGE");
        assertThat(result.path("availabilityStatus").asText()).isEqualTo("UNAVAILABLE");
    }
    @Test void overlappingMetricDeltasReferToOneProviderObservation() {
        var one = new EvidenceSignal(start,"ledger-db","METRIC","counter increased by 3.0","stable-counter",EvidenceSignal.Kind.SYMPTOM,"prometheus","errors",start);
        var two = new EvidenceSignal(start,"ledger-db","METRIC","counter increased by 5.0","stable-counter",EvidenceSignal.Kind.SYMPTOM,"prometheus","errors",start.plusSeconds(10));
        String first = accumulator.merge(null,analysis(one),100,Duration.ofDays(1));
        var next = mapper.readTree(accumulator.merge(first,analysis(two),100,Duration.ofDays(1)));
        assertThat(next.path("timeline").size()).isEqualTo(1);
    }

    @Test void limitsNeverEvictStoredHistoryAndExposeCollectionGap() {
        String first = accumulator.merge(null,analysis(signal(1,"a")),1,Duration.ofDays(1));
        var next = mapper.readTree(accumulator.merge(first,analysis(signal(2,"b")),1,Duration.ofDays(1)));
        assertThat(next.path("timeline").size()).isEqualTo(1);
        assertThat(next.path("collectionLimited").asBoolean()).isTrue();
        assertThat(next.path("warnings").toString()).contains("Previously collected history is retained");
    }
    @Test void emptyRecentWindowRetainsEarlierEvidence() {
        String first = accumulator.merge(null,analysis(signal(1,"a")),100,Duration.ofDays(1));
        var next = mapper.readTree(accumulator.merge(first,analysis(),100,Duration.ofDays(1)));
        assertThat(next.path("timeline").size()).isEqualTo(1);
        assertThat(next.path("origin").path("evidence").size()).isEqualTo(1);
    }
}
