package com.madlanga.blastradius.diagnosis.service;

import com.madlanga.blastradius.diagnosis.dto.DiagnosisResponse;
import com.madlanga.blastradius.diagnosis.mapper.DiagnosisRequestMapper;
import com.madlanga.blastradius.diagnosis.provider.DiagnosisProvider;

import static org.assertj.core.api.Assertions.assertThat;

import com.madlanga.blastradius.diagnosis.provider.DeterministicDiagnosisProvider;
import com.madlanga.blastradius.incident.model.ComponentImpact;
import com.madlanga.blastradius.incident.model.OriginAssessment.Confidence;
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

class DiagnosisServiceTest {
    @Test
    void returnsPrimaryDiagnosisResponseWhenProviderSucceeds() {
        DiagnosisProvider primary = context -> new DiagnosisResponse("gemini", "test", "summary", "cause",
                List.of("now"), List.of("later"), List.of("strategy"), List.of());
        var service = new DiagnosisService(primary, new DeterministicDiagnosisProvider(), new DiagnosisRequestMapper(JsonMapper.builder().build(), new TelemetrySanitizer()));

        assertThat(service.diagnose(analysis()).provider()).isEqualTo("gemini");
    }

    @Test
    void isolatesProviderFailureAndReturnsDeterministicFallback() {
        DiagnosisProvider primary = context -> { throw new IllegalStateException("provider down"); };
        var service = new DiagnosisService(primary, new DeterministicDiagnosisProvider(), new DiagnosisRequestMapper(JsonMapper.builder().build(), new TelemetrySanitizer()));

        var result = service.diagnose(analysis());

        assertThat(result.provider()).isEqualTo("deterministic");
        assertThat(result.limitations()).anyMatch(v -> v.contains("provider down"));
    }

    private IncidentAnalysis analysis() {
        return new IncidentAnalysis(
                "document-platform", "local",
                Instant.parse("2026-10-03T08:21:00Z"), Instant.parse("2026-10-03T08:22:00Z"),
                new OriginAssessment("postgres", OriginAssessment.Confidence.LOW, 0, List.of()),
                TelemetryCoverage.allAvailable(),
                List.of(new ComponentImpact("postgres", ComponentImpact.State.ORIGIN, 0, List.of("postgres"), List.of())),
                List.of(), new IncidentSeverity(IncidentSeverity.Level.HIGH, 50, List.of()), List.of());
    }
}
