package com.madlanga.blastradius.service;

import com.madlanga.blastradius.domain.diagnosis.DiagnosisContext;
import com.madlanga.blastradius.domain.incident.EvidenceSignal;
import com.madlanga.blastradius.domain.incident.IncidentAnalysis;
import java.util.ArrayList;

public final class DiagnosisContextFactory {

    public DiagnosisContext from(IncidentAnalysis analysis) {
        var coverage = analysis.coverage();
        var origin = analysis.origin();
        var severity = analysis.severity();

        var impacts = analysis.impacts().stream()
                .map(impact -> new DiagnosisContext.Impact(
                        impact.component(),
                        impact.state().name(),
                        impact.distance(),
                        impact.path(),
                        impact.evidence().stream().map(this::evidence).toList()))
                .toList();

        var experiment = analysis.experimentAssessment() == null ? null
                : new DiagnosisContext.Experiment(
                        analysis.experimentAssessment().experimentId(),
                        analysis.experimentAssessment().containment().name(),
                        new ArrayList<>(analysis.experimentAssessment().expectedImpact()),
                        new ArrayList<>(analysis.experimentAssessment().observedExpectedImpact()),
                        new ArrayList<>(analysis.experimentAssessment().expectedButUnobserved()),
                        new ArrayList<>(analysis.experimentAssessment().unexpectedImpact()));

        return new DiagnosisContext(
                analysis.applicationId(),
                analysis.environment(),
                analysis.from(),
                analysis.to(),
                new DiagnosisContext.Origin(origin.component(), origin.confidence().name(), origin.evidenceScore()),
                new DiagnosisContext.Coverage(
                        coverage.getLogs().name(),
                        coverage.getMetrics().name(),
                        coverage.getTraces().name(),
                        coverage.getHealth().name(),
                        coverage.isFullyCovered()),
                new DiagnosisContext.Severity(severity.level().name(), severity.score(), severity.reasons()),
                impacts,
                analysis.timeline().stream().map(this::evidence).toList(),
                experiment,
                analysis.warnings());
    }

    private DiagnosisContext.Evidence evidence(EvidenceSignal signal) {
        return new DiagnosisContext.Evidence(
                signal.timestamp(), signal.component(), signal.family(), signal.signal(), signal.evidenceId());
    }
}
