package com.madlanga.blastradius.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.madlanga.blastradius.domain.diagnosis.AiDiagnosis;
import com.madlanga.blastradius.domain.evidence.TelemetryCoverage;
import com.madlanga.blastradius.domain.incident.*;
import com.madlanga.blastradius.ports.AiDiagnosisPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiDiagnosisServiceTest {
    @Test
    void returnsPrimaryAiDiagnosisWhenProviderSucceeds() {
        AiDiagnosisPort primary = context -> new AiDiagnosis("gemini", "test", "summary", "cause",
                List.of("now"), List.of("later"), List.of("strategy"), List.of());
        var service = new AiDiagnosisService(primary, new DeterministicDiagnosisAdapter(), new DiagnosisContextFactory());

        assertThat(service.diagnose(analysis()).provider()).isEqualTo("gemini");
    }

    @Test
    void isolatesProviderFailureAndReturnsDeterministicFallback() {
        AiDiagnosisPort primary = context -> { throw new IllegalStateException("provider down"); };
        var service = new AiDiagnosisService(primary, new DeterministicDiagnosisAdapter(), new DiagnosisContextFactory());

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
                List.of(), new IncidentSeverity(SeverityLevel.HIGH, 50, List.of()), null, List.of());
    }
}
