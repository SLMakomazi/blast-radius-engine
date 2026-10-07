package com.madlanga.blastradius.incident.controller;

import com.madlanga.blastradius.incident.dto.AnalyzeIncidentRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.madlanga.blastradius.incident.model.IncidentAnalysis;
import com.madlanga.blastradius.topology.application.port.DependencyTopologyProvider;
import com.madlanga.blastradius.incident.service.IncidentAnalysisService;
import com.madlanga.blastradius.lifecycle.service.IncidentLifecycleService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    @Mock
    private IncidentAnalysisService service;

    @Mock
    private DependencyTopologyProvider topologyProvider;

    @Mock
    private IncidentLifecycleService lifecycleService;

    @Test
    void postAnalysisDelegatesExplicitRequestWindow() {
        var controller = new IncidentController(service, topologyProvider, lifecycleService);
        Instant from = Instant.parse("2026-10-03T05:20:12Z");
        Instant to = Instant.parse("2026-10-03T05:20:54Z");
        IncidentAnalysis expected = analysis(from, to);
        when(lifecycleService.analyzeAndPersist("document-platform", "local", from, to, null))
                .thenReturn(expected);

        IncidentAnalysis result = controller.analyze(new AnalyzeIncidentRequest(
                "document-platform", "local", from, to, null));

        assertThat(result).isSameAs(expected);
        verify(lifecycleService).analyzeAndPersist("document-platform", "local", from, to, null);
    }

    @Test
    void postAnalysisDefaultsEnvironmentAndWindow() {
        var controller = new IncidentController(service, topologyProvider, lifecycleService);
        IncidentAnalysis expected = analysis(Instant.parse("2026-10-03T05:00:00Z"),
                Instant.parse("2026-10-03T05:15:00Z"));
        when(lifecycleService.analyzeAndPersist(eq("document-platform"), eq("local"), any(Instant.class), any(Instant.class),
                eq("postgres"))).thenReturn(expected);

        controller.analyze(new AnalyzeIncidentRequest(" document-platform ", " ", null, null, " postgres "));

        verify(lifecycleService).analyzeAndPersist(eq("document-platform"), eq("local"), any(Instant.class), any(Instant.class),
                eq("postgres"));
    }

    @Test
    void postAnalysisRejectsMissingApplicationId() {
        var controller = new IncidentController(service, topologyProvider, lifecycleService);

        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() ->
                controller.analyze(new AnalyzeIncidentRequest(" ", "local", null, null, null)));

        assertThat(failure).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("applicationId is required");
    }

    @Test
    void postAnalysisRejectsInvalidWindow() {
        var controller = new IncidentController(service, topologyProvider, lifecycleService);
        Instant at = Instant.parse("2026-10-03T05:20:12Z");

        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() ->
                controller.analyze(new AnalyzeIncidentRequest("document-platform", "local", at, at, null)));

        assertThat(failure).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from must be before to");
    }


    @Test
    void illegalArgumentHandlerClassifiesKnownDomainFailures() {
        var controller = new IncidentController(service, topologyProvider, lifecycleService);

        assertThat(controller.invalidAnalysisRequest(
                new IllegalArgumentException("originHint is not present in discovered topology: missing")).getBody().code())
                .isEqualTo("ORIGIN_NOT_IN_TOPOLOGY");
    }

    @Test
    void illegalStateHandlerDistinguishesMissingEvidence() {
        var controller = new IncidentController(service, topologyProvider, lifecycleService);

        assertThat(controller.analysisUnavailable(
                new IllegalStateException("No failure evidence found in the requested window; provide originHint for theoretical analysis."))
                .getBody().code()).isEqualTo("NO_FAILURE_EVIDENCE");
        assertThat(controller.analysisUnavailable(
                new IllegalStateException("provider unavailable")).getBody().code())
                .isEqualTo("ANALYSIS_NOT_AVAILABLE");
    }

    private IncidentAnalysis analysis(Instant from, Instant to) {
        return new IncidentAnalysis("document-platform", "local", from, to,
                null, null, List.of(), List.of(), null, List.of());
    }
}
