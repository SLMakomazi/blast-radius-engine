package com.madlanga.blastradius.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.madlanga.blastradius.domain.incident.IncidentAnalysis;
import com.madlanga.blastradius.ports.DependencyTopologyProvider;
import com.madlanga.blastradius.service.IncidentAnalysisService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlastRadiusControllerTest {

    @Mock
    private IncidentAnalysisService service;

    @Mock
    private DependencyTopologyProvider topologyProvider;

    @Test
    void postAnalysisDelegatesExplicitRequestWindow() {
        var controller = new BlastRadiusController(service, topologyProvider);
        Instant from = Instant.parse("2026-10-03T05:20:12Z");
        Instant to = Instant.parse("2026-10-03T05:20:54Z");
        IncidentAnalysis expected = analysis(from, to);
        when(service.analyze("document-platform", "local", from, to, null, "postgres-outage-local"))
                .thenReturn(expected);

        IncidentAnalysis result = controller.analyze(new AnalyzeIncidentRequest(
                "document-platform", "local", from, to, null, "postgres-outage-local"));

        assertThat(result).isSameAs(expected);
        verify(service).analyze("document-platform", "local", from, to, null, "postgres-outage-local");
    }

    @Test
    void postAnalysisDefaultsEnvironmentAndWindow() {
        var controller = new BlastRadiusController(service, topologyProvider);
        IncidentAnalysis expected = analysis(Instant.parse("2026-10-03T05:00:00Z"),
                Instant.parse("2026-10-03T05:15:00Z"));
        when(service.analyze(eq("document-platform"), eq("local"), any(Instant.class), any(Instant.class),
                eq("postgres"), eq(null))).thenReturn(expected);

        controller.analyze(new AnalyzeIncidentRequest(" document-platform ", " ", null, null, " postgres ", null));

        verify(service).analyze(eq("document-platform"), eq("local"), any(Instant.class), any(Instant.class),
                eq("postgres"), eq(null));
    }

    @Test
    void postAnalysisRejectsMissingApplicationId() {
        var controller = new BlastRadiusController(service, topologyProvider);

        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() ->
                controller.analyze(new AnalyzeIncidentRequest(" ", "local", null, null, null, null)));

        assertThat(failure).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("applicationId is required");
    }

    @Test
    void postAnalysisRejectsInvalidWindow() {
        var controller = new BlastRadiusController(service, topologyProvider);
        Instant at = Instant.parse("2026-10-03T05:20:12Z");

        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() ->
                controller.analyze(new AnalyzeIncidentRequest("document-platform", "local", at, at, null, null)));

        assertThat(failure).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from must be before to");
    }


    @Test
    void illegalArgumentHandlerClassifiesKnownDomainFailures() {
        var controller = new BlastRadiusController(service, topologyProvider);

        assertThat(controller.invalidAnalysisRequest(
                new IllegalArgumentException("unknown failure experiment: missing")).getBody().code())
                .isEqualTo("UNKNOWN_EXPERIMENT");
        assertThat(controller.invalidAnalysisRequest(
                new IllegalArgumentException("originHint is not present in discovered topology: missing")).getBody().code())
                .isEqualTo("ORIGIN_NOT_IN_TOPOLOGY");
        assertThat(controller.invalidAnalysisRequest(
                new IllegalArgumentException("originHint conflicts with controlled experiment origin: postgres")).getBody().code())
                .isEqualTo("ORIGIN_CONFLICT");
    }

    @Test
    void illegalStateHandlerDistinguishesMissingEvidence() {
        var controller = new BlastRadiusController(service, topologyProvider);

        assertThat(controller.analysisUnavailable(
                new IllegalStateException("No failure evidence found in the requested window; provide originHint for theoretical analysis."))
                .getBody().code()).isEqualTo("NO_FAILURE_EVIDENCE");
        assertThat(controller.analysisUnavailable(
                new IllegalStateException("provider unavailable")).getBody().code())
                .isEqualTo("ANALYSIS_NOT_AVAILABLE");
    }

    private IncidentAnalysis analysis(Instant from, Instant to) {
        return new IncidentAnalysis("document-platform", "local", from, to,
                null, null, List.of(), List.of(), null, null, List.of());
    }
}
