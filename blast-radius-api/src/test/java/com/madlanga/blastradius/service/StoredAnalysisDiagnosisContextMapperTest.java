package com.madlanga.blastradius.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.madlanga.blastradius.sanitization.TelemetrySanitizer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StoredAnalysisDiagnosisContextMapperTest {

    private final StoredAnalysisDiagnosisContextMapper mapper =
            new StoredAnalysisDiagnosisContextMapper(JsonMapper.builder().build(), new TelemetrySanitizer());

    @Test
    void mapsPersistedSnapshotDirectlyWithoutReconstructingDomainObjects() {
        var context = mapper.fromJson(snapshot(
                "database connection failed",
                "live health snapshots outside incident window excluded"));

        assertThat(context.applicationId()).isEqualTo("document-platform");
        assertThat(context.environment()).isEqualTo("local");
        assertThat(context.origin().component()).isEqualTo("postgres");
        assertThat(context.origin().confidence()).isEqualTo("HIGH");
        assertThat(context.coverage().fullyCovered()).isTrue();
        assertThat(context.severity().level()).isEqualTo("HIGH");
        assertThat(context.severity().score()).isEqualTo(50);
        assertThat(context.impacts()).extracting(impact -> impact.component())
                .containsExactly("postgres", "document-service");
        assertThat(context.timeline()).hasSize(1);
        assertThat(context.experiment()).isNotNull();
        assertThat(context.experiment().containment()).isEqualTo("HELD");
    }

    @Test
    void sanitizesFreeTextAgainBeforeItCanReachAiProvider() {
        var context = mapper.fromJson(snapshot(
                "database failed Authorization: Bearer super-secret-token",
                "operator note Authorization: Bearer another-secret"));

        assertThat(context.timeline().getFirst().signal())
                .isEqualTo("database failed Authorization: [REDACTED]");
        assertThat(context.warnings().getFirst())
                .isEqualTo("operator note Authorization: [REDACTED]");
        assertThat(context.timeline().getFirst().signal()).doesNotContain("super-secret-token");
        assertThat(context.warnings().getFirst()).doesNotContain("another-secret");
    }

    private String snapshot(String signal, String warning) {
        return """
                {
                  "applicationId": "document-platform",
                  "environment": "local",
                  "from": "2026-10-03T09:40:00Z",
                  "to": "2026-10-03T09:45:17Z",
                  "origin": {
                    "component": "postgres",
                    "confidence": "HIGH",
                    "evidenceScore": 40
                  },
                  "coverage": {
                    "logs": "AVAILABLE",
                    "metrics": "AVAILABLE",
                    "traces": "AVAILABLE",
                    "health": "AVAILABLE",
                    "fullyCovered": true
                  },
                  "severity": {
                    "level": "HIGH",
                    "score": 50,
                    "reasons": ["3 dependent components observed"]
                  },
                  "impacts": [
                    {
                      "component": "postgres",
                      "state": "ORIGIN",
                      "distance": 0,
                      "path": ["postgres"],
                      "evidence": []
                    },
                    {
                      "component": "document-service",
                      "state": "OBSERVED",
                      "distance": 1,
                      "path": ["postgres", "document-service"],
                      "evidence": []
                    }
                  ],
                  "timeline": [
                    {
                      "timestamp": "2026-10-03T09:41:11Z",
                      "component": "document-service",
                      "family": "LOG",
                      "signal": "%s",
                      "evidenceId": "log-1"
                    }
                  ],
                  "experimentAssessment": {
                    "experimentId": "postgres-outage-local",
                    "containment": "HELD",
                    "expectedImpact": ["postgres", "document-service"],
                    "observedExpectedImpact": ["postgres", "document-service"],
                    "expectedButUnobserved": [],
                    "unexpectedImpact": []
                  },
                  "warnings": ["%s"]
                }
                """.formatted(signal, warning);
    }
}
