package com.madlanga.blastradius.diagnosis.mapper;

import com.madlanga.blastradius.diagnosis.dto.DiagnosisRequest;

import static org.assertj.core.api.Assertions.assertThat;

import com.madlanga.blastradius.incident.model.ComponentImpact;
import com.madlanga.blastradius.incident.model.OriginAssessment.Confidence;
import com.madlanga.blastradius.incident.model.EvidenceSignal;
import com.madlanga.blastradius.incident.model.IncidentAnalysis;
import com.madlanga.blastradius.incident.model.IncidentSeverity;
import com.madlanga.blastradius.incident.model.ComponentImpact.State;
import com.madlanga.blastradius.incident.model.OriginAssessment;
import com.madlanga.blastradius.incident.model.IncidentSeverity.Level;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;
import com.madlanga.blastradius.telemetry.model.TelemetryCoverage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DiagnosisRequestMapperTest {
    @Test
    void mapsOnlyDeterministicAnalysisIntoDiagnosisRequest() {
        var evidence = new EvidenceSignal(Instant.parse("2026-10-03T08:21:28Z"), "postgres", "HEALTH", "health DOWN", "h1");
        var analysis = new IncidentAnalysis(
                "document-platform", "local",
                Instant.parse("2026-10-03T08:21:00Z"), Instant.parse("2026-10-03T08:22:00Z"),
                new OriginAssessment("postgres", OriginAssessment.Confidence.HIGH, 90, List.of(evidence)),
                TelemetryCoverage.allAvailable(),
                List.of(new ComponentImpact("postgres", ComponentImpact.State.ORIGIN, 0, List.of("postgres"), List.of(evidence))),
                List.of(evidence),
                new IncidentSeverity(IncidentSeverity.Level.HIGH, 50, List.of("observed propagation")),
                List.of());

        var result = new DiagnosisRequestMapper(JsonMapper.builder().build(), new TelemetrySanitizer()).from(analysis);

        assertThat(result.origin().component()).isEqualTo("postgres");
        assertThat(result.severity().score()).isEqualTo(50);
        assertThat(result.coverage().fullyCovered()).isTrue();
        assertThat(result.impacts()).hasSize(1);
        assertThat(result.timeline()).hasSize(1);
    }
}
