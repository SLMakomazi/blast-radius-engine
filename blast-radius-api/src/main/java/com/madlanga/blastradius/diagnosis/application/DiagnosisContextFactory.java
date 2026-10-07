package com.madlanga.blastradius.diagnosis.application;

import com.madlanga.blastradius.diagnosis.domain.DiagnosisContext;
import com.madlanga.blastradius.incident.domain.EvidenceSignal;
import com.madlanga.blastradius.incident.domain.IncidentAnalysis;

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
                analysis.warnings());
    }

    private DiagnosisContext.Evidence evidence(EvidenceSignal signal) {
        return new DiagnosisContext.Evidence(
                signal.timestamp(), signal.component(), signal.family(), signal.signal(), signal.evidenceId());
    }
}
