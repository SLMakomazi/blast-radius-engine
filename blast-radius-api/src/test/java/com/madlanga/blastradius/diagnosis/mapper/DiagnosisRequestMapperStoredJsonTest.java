package com.madlanga.blastradius.diagnosis.mapper;

import com.madlanga.blastradius.diagnosis.dto.DiagnosisRequest;

import static org.assertj.core.api.Assertions.assertThat;

import com.madlanga.blastradius.shared.sanitization.TelemetrySanitizer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DiagnosisRequestMapperStoredJsonTest {

    private final DiagnosisRequestMapper mapper =
            new DiagnosisRequestMapper(JsonMapper.builder().build(), new TelemetrySanitizer());

    @Test
    void mapsPersistedSnapshotDirectlyWithoutReconstructingDomainObjects() {
        var context = mapper.fromStoredJson(snapshot(
                "database connection failed",
                "live health snapshots outside incident window excluded"));

        assertThat(context.applicationId()).isEqualTo("document-platform");
        assertThat(context.environment()).isEqualTo("local");
        assertThat(context.origin().component()).isEqualTo("postgres");
        assertThat(context.origin().confidence()).isEqualTo("HIGH");
        assertThat(context.coverage().fullyCovered()).isTrue();
        assertThat(context.severity().level()).isEqualTo("HIGH");
        assertThat(context.severity().score()).isEqualTo(50);
        assertThat(context.impacts()).extracting(DiagnosisRequest.Impact::component)
                .containsExactly("postgres", "storage-api");
        assertThat(context.timeline()).hasSize(1);
    }

    @Test
    void sanitizesFreeTextAgainBeforeItCanReachAiProvider() {
        var context = mapper.fromStoredJson(snapshot(
                "database failed Authorization: Bearer super-secret-token",
                "operator note Authorization: Bearer another-secret"));

        assertThat(context.timeline().getFirst().signal())
                .isEqualTo("database failed Authorization: [REDACTED]");
        assertThat(context.warnings().getFirst())
                .isEqualTo("operator note Authorization: [REDACTED]");
        assertThat(context.timeline().getFirst().signal()).doesNotContain("super-secret-token");
        assertThat(context.warnings().getFirst()).doesNotContain("another-secret");
    }

    @Test
    void cappedRecoveryProofReachesAiInputAndIsSanitizedAndDeduplicated() {
        String base = snapshot("availability health DOWN", "evidence cap reached");
        String recovery = """
            ,"lastRecoveryEvidence":{
              "timestamp":"2026-10-03T09:44:59.123456Z",
              "component":"postgres","family":"HEALTH",
              "signal":"availability health UP Authorization: Bearer test-secret",
              "evidenceId":"up-1","kind":"AVAILABILITY_AVAILABLE",
              "provider":"direct-jdbc","sourceRef":"database/availability"
            }
            """;
        String capped = base.substring(0,base.lastIndexOf('}')) + recovery + "}";
        var context = mapper.fromStoredJson(capped);
        assertThat(context.timeline()).hasSize(2);
        assertThat(context.timeline().stream().filter(e -> "AVAILABILITY_AVAILABLE".equals(e.kind())).toList())
                .singleElement().satisfies(e -> {
                    assertThat(e.component()).isEqualTo("postgres");
                    assertThat(e.signal()).contains("[REDACTED]").doesNotContain("test-secret");
                });
        assertThat(context.timeline().stream().filter(e -> "AVAILABILITY_AVAILABLE".equals(e.kind())).count())
                .isEqualTo(1);
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
                      "component": "storage-api",
                      "state": "OBSERVED",
                      "distance": 1,
                      "path": ["postgres", "storage-api"],
                      "evidence": []
                    }
                  ],
                  "timeline": [
                    {
                      "timestamp": "2026-10-03T09:41:11Z",
                      "component": "storage-api",
                      "family": "LOG",
                      "signal": "%s",
                      "evidenceId": "log-1"
                    }
                  ],
                  "warnings": ["%s"]
                }
                """.formatted(signal, warning);
    }
}
