package com.madlanga.blastradius.diagnosis.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.madlanga.blastradius.incident.domain.ComponentImpact;
import com.madlanga.blastradius.incident.domain.ConfidenceLevel;
import com.madlanga.blastradius.incident.domain.EvidenceSignal;
import com.madlanga.blastradius.incident.domain.IncidentAnalysis;
import com.madlanga.blastradius.incident.domain.IncidentSeverity;
import com.madlanga.blastradius.incident.domain.ObservedState;
import com.madlanga.blastradius.incident.domain.OriginAssessment;
import com.madlanga.blastradius.incident.domain.SeverityLevel;
import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;
import com.madlanga.blastradius.telemetry.domain.TelemetryCoverage;
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
                new OriginAssessment("postgres", ConfidenceLevel.HIGH, 90, List.of(evidence)),
                TelemetryCoverage.allAvailable(),
                List.of(new ComponentImpact("postgres", ObservedState.ORIGIN, 0, List.of("postgres"), List.of(evidence))),
                List.of(evidence),
                new IncidentSeverity(SeverityLevel.HIGH, 50, List.of("observed propagation")),
                List.of());

        var result = new DiagnosisRequestMapper(JsonMapper.builder().build(), new TelemetrySanitizer()).from(analysis);

        assertThat(result.origin().component()).isEqualTo("postgres");
        assertThat(result.severity().score()).isEqualTo(50);
        assertThat(result.coverage().fullyCovered()).isTrue();
        assertThat(result.impacts()).hasSize(1);
        assertThat(result.timeline()).hasSize(1);
    }
}
