package com.madlanga.blastradius.service;

import com.madlanga.blastradius.ports.DependencyTopologyProvider;
import com.madlanga.blastradius.domain.evidence.*;
import com.madlanga.blastradius.domain.incident.*;
import com.madlanga.blastradius.domain.experiment.*;
import com.madlanga.blastradius.ports.FailureExperimentProvider;
import com.madlanga.blastradius.domain.topology.*;
import com.madlanga.blastradius.ports.TelemetryProvider;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Phase 6 deterministic incident intelligence.
 * AI is deliberately absent: evidence determines origin candidates and observed impact.
 */
@Service
public class IncidentAnalysisService {
    private final TelemetryProvider telemetryProvider;
    private final DependencyTopologyProvider topologyProvider;
    private final DeterministicGraphEngine graphEngine = new DeterministicGraphEngine();
    private final IncidentSeverityCalculator severityCalculator = new IncidentSeverityCalculator();
    private final FailureExperimentAssessmentService experimentAssessmentService = new FailureExperimentAssessmentService();
    private final FailureExperimentProvider failureExperimentProvider;

    public IncidentAnalysisService(TelemetryProvider telemetryProvider, DependencyTopologyProvider topologyProvider) {
        this(telemetryProvider, topologyProvider, experimentId -> Optional.empty());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public IncidentAnalysisService(TelemetryProvider telemetryProvider, DependencyTopologyProvider topologyProvider,
            FailureExperimentProvider failureExperimentProvider) {
        this.telemetryProvider = telemetryProvider;
        this.topologyProvider = topologyProvider;
        this.failureExperimentProvider = failureExperimentProvider;
    }

    public IncidentAnalysis analyze(String applicationId, String environment, Instant from, Instant to, String originHint) {
        return analyze(applicationId, environment, from, to, originHint, null);
    }

    public IncidentAnalysis analyze(String applicationId, String environment, Instant from, Instant to,
            String originHint, String experimentId) {
        FailureExperiment experiment = resolveExperiment(experimentId);
        String effectiveOriginHint = experiment == null ? originHint : experiment.originComponent();
        if (experiment != null && hasText(originHint) && !originHint.trim().equals(experiment.originComponent())) {
            throw new IllegalArgumentException("originHint conflicts with controlled experiment origin: " + experiment.originComponent());
        }
        TelemetryQuery query = TelemetryQuery.builder().applicationId(applicationId).environment(environment)
                .from(from).to(to).build();
        TelemetryBundle telemetry = withinWindow(telemetryProvider.getTelemetry(query), from, to);

        DependencyTopology topology = topologyProvider.getTopology(applicationId, environment);

        Map<String,List<EvidenceSignal>> signals = correlate(telemetry, topology);
        OriginAssessment origin = assessOrigin(effectiveOriginHint, topology, signals);
        GraphAnalysisResult theoretical = graphEngine.calculate(topology, origin.component());

        Map<String,TheoreticalImpact> theoreticalById = new LinkedHashMap<>();
        theoretical.getImpacts().forEach(i -> theoreticalById.put(i.getComponent().getId(), i));

        List<ComponentImpact> impacts = new ArrayList<>();
        impacts.add(new ComponentImpact(origin.component(), ObservedState.ORIGIN, 0,
                List.of(origin.component()), origin.evidence()));

        for (TheoreticalImpact impact : theoretical.getImpacts()) {
            List<EvidenceSignal> componentSignals = signals.getOrDefault(impact.getComponent().getId(), List.of());
            ObservedState state = componentSignals.isEmpty()
                    ? (telemetry.isFullyCovered() ? ObservedState.THEORETICAL_ONLY : ObservedState.UNKNOWN)
                    : ObservedState.OBSERVED;
            impacts.add(new ComponentImpact(impact.getComponent().getId(), state, impact.getDistance(),
                    impact.getPath(), componentSignals));
        }

        signals.entrySet().stream()
                .filter(e -> !e.getKey().equals(origin.component()) && !theoreticalById.containsKey(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> impacts.add(new ComponentImpact(e.getKey(), ObservedState.UNEXPECTED, null,
                        List.of(), e.getValue())));

        List<EvidenceSignal> timeline = signals.values().stream().flatMap(Collection::stream)
                .sorted(Comparator.comparing(EvidenceSignal::timestamp)
                        .thenComparing(EvidenceSignal::component)
                        .thenComparing(EvidenceSignal::family))
                .toList();

        List<String> warnings = new ArrayList<>(telemetry.getWarnings());
        if (!telemetry.isFullyCovered()) warnings.add("Observed impact is constrained by partial telemetry coverage; missing evidence is not healthy evidence.");

        IncidentSeverity severity = severityCalculator.calculate(topology, origin, impacts);
        ExperimentAssessment experimentAssessment = experiment == null ? null
                : experimentAssessmentService.assess(experiment, impacts, telemetry.isFullyCovered());
        return new IncidentAnalysis(applicationId, environment, from, to, origin,
                telemetry.getCoverage(), impacts, timeline, severity, experimentAssessment, warnings);
    }

    private FailureExperiment resolveExperiment(String experimentId) {
        if (!hasText(experimentId)) return null;
        return failureExperimentProvider.getExperiment(experimentId.trim())
                .orElseThrow(() -> new IllegalArgumentException("unknown failure experiment: " + experimentId.trim()));
    }

    private TelemetryBundle withinWindow(TelemetryBundle bundle, Instant from, Instant to) {
        java.util.function.Predicate<Instant> inWindow = at -> !at.isBefore(from) && at.isBefore(to);
        List<String> warnings = new ArrayList<>(bundle.getWarnings());
        if (bundle.getHealth().stream().anyMatch(h -> !inWindow.test(h.getTimestamp())))
            warnings.add("Live health snapshots outside the incident window were excluded from correlation; coverage describes provider availability.");
        return TelemetryBundle.builder().coverage(bundle.getCoverage()).warnings(warnings)
                .spans(bundle.getSpans().stream().filter(s -> inWindow.test(s.getStartTime())).toList())
                .logs(bundle.getLogs().stream().filter(l -> inWindow.test(l.getTimestamp())).toList())
                .metrics(bundle.getMetrics().stream().filter(m -> inWindow.test(m.getTimestamp())).toList())
                .health(bundle.getHealth().stream().filter(h -> inWindow.test(h.getTimestamp())).toList()).build();
    }

    private Map<String,List<EvidenceSignal>> correlate(TelemetryBundle telemetry, DependencyTopology topology) {
        Map<String,List<EvidenceSignal>> result = new LinkedHashMap<>();
        for (LogEvidence log : telemetry.getLogs()) {
            if (isErrorLevel(log.getLevel()) || isStage2DegradationLog(log)) add(result, log.getService(),
                    new EvidenceSignal(log.getTimestamp(), log.getService(), "LOG",
                            logSignal(log), log.getId()));
        }
        for (SpanEvidence span : telemetry.getSpans()) {
            if (span.getDurationMs() >= 2000) {
                add(result, span.getService(), new EvidenceSignal(span.getStartTime(), span.getService(), "TRACE",
                        "slow span: " + span.getOperation() + " duration=" + span.getDurationMs() + "ms", span.getId()));
            }
            if (span.isError()) {
                add(result, span.getService(), new EvidenceSignal(span.getStartTime(), span.getService(), "TRACE",
                        "error span: " + span.getOperation(), span.getId()));
                if (hasText(span.getPeerService())) {
                    add(result, span.getPeerService(),
                            new EvidenceSignal(span.getStartTime(), span.getPeerService(), "TRACE",
                                    "dependency error observed by " + span.getService(), span.getId()));
                } else {
                    inferFailedDependencyFromTopology(span, topology).ifPresent(dependency ->
                            add(result, dependency,
                                    new EvidenceSignal(span.getStartTime(), dependency, "TRACE",
                                            "dependency error inferred from topology for failed "
                                                    + span.getOperation() + " observed by " + span.getService(),
                                            span.getId())));
                }
            }
        }
        for (HealthEvidence health : telemetry.getHealth()) {
            if (health.isDegraded()) add(result, health.getService(),
                    new EvidenceSignal(health.getTimestamp(), health.getService(), "HEALTH",
                            "health " + health.getState(), health.getId()));
        }
        correlateMetricDeltas(telemetry.getMetrics(), result);
        result.replaceAll((k,v) -> v.stream().sorted(Comparator.comparing(EvidenceSignal::timestamp)).toList());
        return result;
    }

    private Optional<String> inferFailedDependencyFromTopology(SpanEvidence span, DependencyTopology topology) {
        // A server error alone does not prove that a dependency failed.
        if (span.getKind() != SpanKind.INTERNAL && span.getKind() != SpanKind.CLIENT
                && span.getKind() != SpanKind.PRODUCER) return Optional.empty();
        List<String> dependencies = topology.getEdges().stream()
                .filter(edge -> edge.getDependentId().equals(span.getService()))
                .map(DependencyEdge::getDependencyId)
                .sorted()
                .toList();

        // A peer-less failed span proves that the emitting component failed while using
        // a dependency, but it does not identify which dependency when several exist.
        // Only infer when topology leaves exactly one possible downstream dependency.
        return dependencies.size() == 1 ? Optional.of(dependencies.get(0)) : Optional.empty();
    }

    private OriginAssessment assessOrigin(String hint, DependencyTopology topology, Map<String,List<EvidenceSignal>> signals) {
        if (hasText(hint)) {
            String id=hint.trim();
            if (topology.getNode(id)==null) throw new IllegalArgumentException("originHint is not present in discovered topology: "+id);
            List<EvidenceSignal> evidence=signals.getOrDefault(id,List.of());
            return new OriginAssessment(id, evidence.isEmpty()?ConfidenceLevel.LOW:ConfidenceLevel.HIGH, score(evidence), evidence);
        }
        Set<String> candidates = new LinkedHashSet<>();
        signals.keySet().stream().filter(id -> topology.getNode(id) != null).forEach(candidates::add);
        if (candidates.isEmpty()) throw new IllegalStateException(
                "No failure evidence found in the requested window; provide originHint for theoretical analysis.");

        Set<String> hasFailingDependency = new HashSet<>();
        for (DependencyEdge edge : topology.getEdges()) {
            if (candidates.contains(edge.getDependentId()) && candidates.contains(edge.getDependencyId())) {
                hasFailingDependency.add(edge.getDependentId());
            }
        }
        List<String> rootCandidates = candidates.stream().filter(id -> !hasFailingDependency.contains(id)).toList();
        if (rootCandidates.isEmpty()) rootCandidates = List.copyOf(candidates);

        return rootCandidates.stream()
                .map(id -> new OriginAssessment(id, confidence(score(signals.get(id))),
                        score(signals.get(id)), signals.get(id)))
                .sorted(Comparator.comparingInt(OriginAssessment::evidenceScore).reversed()
                        .thenComparing(o -> earliest(o.evidence())).thenComparing(OriginAssessment::component))
                .findFirst().orElseThrow();
    }

    private int score(List<EvidenceSignal> evidence) {
        int score=0;
        for(EvidenceSignal s:evidence) score += switch(s.family()) {
            case "HEALTH" -> 50;
            case "TRACE" -> s.signal().startsWith("dependency error observed by") ? 80 : 40;
            case "LOG" -> 30;
            case "METRIC" -> 20;
            default -> 0;
        };
        return score;
    }
    private ConfidenceLevel confidence(int score) { return score>=80?ConfidenceLevel.HIGH:score>=40?ConfidenceLevel.MEDIUM:ConfidenceLevel.LOW; }
    private Instant earliest(List<EvidenceSignal> evidence) { return evidence.stream().map(EvidenceSignal::timestamp).min(Instant::compareTo).orElse(Instant.MAX); }
    private void add(Map<String,List<EvidenceSignal>> map,String service,EvidenceSignal signal) { if(hasText(service)&&!"unknown".equalsIgnoreCase(service)) map.computeIfAbsent(service,k->new ArrayList<>()).add(signal); }
    private boolean isErrorLevel(String level) { return level!=null && ("ERROR".equalsIgnoreCase(level)||"FATAL".equalsIgnoreCase(level)); }
    private boolean isStage2DegradationLog(LogEvidence log) {
        String message = log.getMessage() == null ? "" : log.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("synthetic_latency");
    }
    private String logSignal(LogEvidence log) {
        String message = log.getMessage() == null ? "" : log.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("synthetic_application_failure") && message.contains("intermittent_500"))
            return "stage2 intermittent HTTP 500";
        if (message.contains("synthetic_application_failure") || message.contains("synthetic local application failure"))
            return "stage2 application error HTTP 500";
        if (message.contains("synthetic_dependency_failure"))
            return "stage2 database connectivity failure";
        if (message.contains("synthetic_latency"))
            return "stage2 latency degradation";
        return log.getLevel() + " log";
    }
    private void correlateMetricDeltas(List<MetricEvidence> metrics, Map<String,List<EvidenceSignal>> result) {
        Map<String,List<MetricEvidence>> series = new LinkedHashMap<>();
        for (MetricEvidence m : metrics) {
            String key=m.getService()+"|"+m.getName()+"|"+metricIdentity(m.getDimensions());
            series.computeIfAbsent(key,k->new ArrayList<>()).add(m);
        }

        // Existing counter-based failure evidence: 5xx and connection timeouts.
        for (List<MetricEvidence> points : series.values()) {
            points.sort(Comparator.comparing(MetricEvidence::getTimestamp));
            if (points.size()<2) continue;
            MetricEvidence first=points.get(0), last=points.get(points.size()-1);
            double delta=last.getValue()-first.getValue();
            if (delta<=0) continue;

            String status=last.getDimensions().getOrDefault("status",last.getDimensions().get("code"));
            String name=last.getName().toLowerCase(Locale.ROOT);
            boolean serverErrorCounter=status!=null && status.matches("5\\d\\d")
                    && name.endsWith(".count");
            boolean timeoutCounter=name.contains("timeout")
                    && (name.endsWith(".total") || name.endsWith(".count"));

            if (serverErrorCounter) {
                add(result,last.getService(),new EvidenceSignal(last.getTimestamp(),last.getService(),
                        "METRIC",last.getName()+" 5xx counter increased by "+delta,last.getId()));
            } else if (timeoutCounter && hasIndependentFailureSignal(result,last.getService())) {
                add(result,last.getService(),new EvidenceSignal(last.getTimestamp(),last.getService(),
                        "METRIC",last.getName()+" timeout counter increased by "+delta,last.getId()));
            }
        }

        correlateHttpLatency(series, result);
        correlateSustainedResourcePressure(series, result);
    }

    /**
     * Detect degraded-but-running HTTP services from the delta of Micrometer sum/count
     * series. A 2 second mean latency is deliberately conservative for this synthetic
     * lab and avoids treating a historical max value as a fresh incident.
     */
    private void correlateHttpLatency(Map<String,List<MetricEvidence>> series,
                                      Map<String,List<EvidenceSignal>> result) {
        Map<String,List<MetricEvidence>> counts = new LinkedHashMap<>();
        Map<String,List<MetricEvidence>> sums = new LinkedHashMap<>();

        for (List<MetricEvidence> points : series.values()) {
            if (points.isEmpty()) continue;
            MetricEvidence sample = points.get(0);
            String name = sample.getName().toLowerCase(Locale.ROOT);
            if (!name.startsWith("http.") || (!name.endsWith(".count") && !name.endsWith(".sum"))) continue;

            String baseName = name.substring(0, name.lastIndexOf('.'));
            String key = sample.getService()+"|"+baseName+"|"+metricIdentity(sample.getDimensions());
            (name.endsWith(".count") ? counts : sums).put(key, points);
        }

        for (Map.Entry<String,List<MetricEvidence>> entry : counts.entrySet()) {
            List<MetricEvidence> countPoints = entry.getValue();
            List<MetricEvidence> sumPoints = sums.get(entry.getKey());
            if (sumPoints == null || countPoints.size()<2 || sumPoints.size()<2) continue;

            countPoints.sort(Comparator.comparing(MetricEvidence::getTimestamp));
            sumPoints.sort(Comparator.comparing(MetricEvidence::getTimestamp));
            MetricEvidence firstCount=countPoints.get(0), lastCount=countPoints.get(countPoints.size()-1);
            MetricEvidence firstSum=sumPoints.get(0), lastSum=sumPoints.get(sumPoints.size()-1);
            double requestDelta=lastCount.getValue()-firstCount.getValue();
            double secondsDelta=lastSum.getValue()-firstSum.getValue();
            if (requestDelta<=0 || secondsDelta<0) continue;

            double meanSeconds=secondsDelta/requestDelta;
            if (meanSeconds>=2.0) {
                add(result,lastCount.getService(),new EvidenceSignal(lastCount.getTimestamp(),
                        lastCount.getService(),"METRIC",
                        String.format(Locale.ROOT,"HTTP mean latency %.3fs across %.0f requests",meanSeconds,requestDelta),
                        lastCount.getId()));
            }
        }
    }

    /**
     * Resource signals are only promoted when pressure is sustained across at least
     * two samples. Memory-used alone is intentionally not interpreted because without
     * a configured/max value it cannot prove memory pressure.
     */
    private void correlateSustainedResourcePressure(Map<String,List<MetricEvidence>> series,
                                                    Map<String,List<EvidenceSignal>> result) {
        for (List<MetricEvidence> points : series.values()) {
            if (points.size()<2) continue;
            points.sort(Comparator.comparing(MetricEvidence::getTimestamp));
            MetricEvidence last=points.get(points.size()-1);
            String name=last.getName().toLowerCase(Locale.ROOT);

            if ("process.cpu.usage".equals(name)) {
                long high=points.stream().filter(p -> p.getValue()>=0.90).count();
                if (high>=2) {
                    add(result,last.getService(),new EvidenceSignal(last.getTimestamp(),last.getService(),
                            "METRIC","sustained process CPU usage >= 90%",last.getId()));
                }
            } else if ("hikaricp.connections.pending".equals(name)) {
                long pending=points.stream().filter(p -> p.getValue()>0).count();
                if (pending>=2) {
                    add(result,last.getService(),new EvidenceSignal(last.getTimestamp(),last.getService(),
                            "METRIC","sustained database connection-pool contention",last.getId()));
                }
            }
        }
    }

    private Map<String,String> metricIdentity(Map<String,String> dimensions) {
        Map<String,String> identity = new TreeMap<>();
        dimensions.forEach((key,value) -> {
            // Micrometer histogram/Prometheus series may include labels that vary
            // between samples. Keep only dimensions that identify the HTTP route
            // and outcome so counter deltas can be calculated across scrapes.
            if (Set.of("method","uri","status","outcome","exception","error").contains(key)) {
                identity.put(key,value);
            }
        });
        return identity;
    }

    private boolean hasIndependentFailureSignal(Map<String,List<EvidenceSignal>> result, String service) {
        return result.getOrDefault(service,List.of()).stream()
                .anyMatch(signal -> !"METRIC".equals(signal.family()));
    }
    private boolean hasText(String value) { return value!=null&&!value.isBlank(); }
}
