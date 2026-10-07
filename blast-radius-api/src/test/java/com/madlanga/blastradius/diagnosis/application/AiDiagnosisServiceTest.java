package com.madlanga.blastradius.diagnosis;

import com.madlanga.blastradius.diagnosis.provider.DeterministicDiagnosisProvider;

import static org.assertj.core.api.Assertions.assertThat;
import com.madlanga.blastradius.telemetry.domain.TelemetryCoverage;
import com.madlanga.blastradius.incident.domain.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiDiagnosisServiceTest {
    @Test
    void returnsPrimaryAiDiagnosisWhenProviderSucceeds() {
        AiDiagnosisProvider primary = context -> new AiDiagnosis("gemini", "test", "summary", "cause",
                List.of("now"), List.of("later"), List.of("strategy"), List.of());
        var service = new AiDiagnosisService(primary, new DeterministicDiagnosisProvider(), new DiagnosisContextMapper(tools.jackson.databind.json.JsonMapper.builder().build(), new com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer()));

        assertThat(service.diagnose(analysis()).provider()).isEqualTo("gemini");
    }

    @Test
    void isolatesProviderFailureAndReturnsDeterministicFallback() {
        AiDiagnosisProvider primary = context -> { throw new IllegalStateException("provider down"); };
        var service = new AiDiagnosisService(primary, new DeterministicDiagnosisProvider(), new DiagnosisContextMapper(tools.jackson.databind.json.JsonMapper.builder().build(), new com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer()));

        var result = service.diagnose(analysis());

        assertThat(result.provider()).isEqualTo("deterministic");
        assertThat(result.limitations()).anyMatch(v -> v.contains("provider down"));
    }

    private IncidentAnalysis analysis() {
        return new IncidentAnalysis(
                "document-platform", "local",
                Instant.parse("2026-10-03T08:21:00Z"), Instant.parse("2026-10-03T08:22:00Z"),
                new OriginAssessment("postgres", ConfidenceLevel.LOW, 0, List.of()),
                TelemetryCoverage.allAvailable(),
                List.of(new ComponentImpact("postgres", ObservedState.ORIGIN, 0, List.of("postgres"), List.of())),
                List.of(), new IncidentSeverity(SeverityLevel.HIGH, 50, List.of()), List.of());
    }
}
