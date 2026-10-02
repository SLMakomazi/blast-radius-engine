package com.madlanga.blastradius.service;

import com.madlanga.blastradius.adapters.topology.TraceDiscoveredTopologyProvider;
import com.madlanga.blastradius.domain.evidence.*;
import com.madlanga.blastradius.domain.incident.*;
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
    private static final java.time.Duration TOPOLOGY_LOOKBACK = java.time.Duration.ofHours(1);

    private final TelemetryProvider telemetryProvider;
    private final DeterministicGraphEngine graphEngine = new DeterministicGraphEngine();

    public IncidentAnalysisService(TelemetryProvider telemetryProvider) {
        this.telemetryProvider = telemetryProvider;
    }

    public IncidentAnalysis analyze(String applicationId, String environment, Instant from, Instant to, String originHint) {
        TelemetryQuery query = TelemetryQuery.builder().applicationId(applicationId).environment(environment)
                .from(from).to(to).build();
        TelemetryBundle telemetry = telemetryProvider.getTelemetry(query);

        // Incident evidence and topology discovery intentionally use different windows.
        // A dependency that is already unavailable may stop producing successful spans
        // that identify its peer. Reconstruct topology from evidence immediately before
        // the incident window, then merge those spans with the current incident spans.
        // Historical telemetry affects topology only; it must not become incident evidence
        // or alter the requested window's coverage.
        TelemetryQuery topologyQuery = TelemetryQuery.builder()
                .applicationId(applicationId)
                .environment(environment)
                .from(from.minus(TOPOLOGY_LOOKBACK))
                .to(from)
                .build();
        TelemetryBundle topologyTelemetry = telemetryProvider.getTelemetry(topologyQuery);
        List<SpanEvidence> topologySpans = new ArrayList<>(topologyTelemetry.getSpans());
        topologySpans.addAll(telemetry.getSpans());

        DependencyTopology topology = new TraceDiscoveredTopologyProvider(telemetryProvider)
                .discover(applicationId, environment, topologySpans);

        Map<String,List<EvidenceSignal>> signals = correlate(telemetry, topology);
        OriginAssessment origin = assessOrigin(originHint, topology, signals);
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

        return new IncidentAnalysis(applicationId, environment, from, to, origin,
                telemetry.getCoverage(), impacts, timeline, warnings);
    }

    private Map<String,List<EvidenceSignal>> correlate(TelemetryBundle telemetry, DependencyTopology topology) {
        Map<String,List<EvidenceSignal>> result = new LinkedHashMap<>();
        for (LogEvidence log : telemetry.getLogs()) {
            if (isErrorLevel(log.getLevel())) add(result, log.getService(),
                    new EvidenceSignal(log.getTimestamp(), log.getService(), "LOG",
                            log.getLevel() + " log", log.getId()));
        }
        for (SpanEvidence span : telemetry.getSpans()) {
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
    private void correlateMetricDeltas(List<MetricEvidence> metrics, Map<String,List<EvidenceSignal>> result) {
        Map<String,List<MetricEvidence>> series = new LinkedHashMap<>();
        for (MetricEvidence m : metrics) {
            String status=m.getDimensions().getOrDefault("status",m.getDimensions().get("code"));
            boolean candidate=(status!=null && status.matches("5\\d\\d"))
                    || m.getName().toLowerCase(Locale.ROOT).contains("timeout");
            if (!candidate) continue;
            String key=m.getService()+"|"+m.getName()+"|"+m.getDimensions();
            series.computeIfAbsent(key,k->new ArrayList<>()).add(m);
        }
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

            // HTTP *_sum/_max series are latency/accumulator signals, not failure counts.
            // Timeout counters are supporting evidence only: require another independent
            // failure family for the same component before promoting them to observed impact.
            if (serverErrorCounter) {
                add(result,last.getService(),new EvidenceSignal(last.getTimestamp(),last.getService(),
                        "METRIC",last.getName()+" 5xx counter increased by "+delta,last.getId()));
            } else if (timeoutCounter && hasIndependentFailureSignal(result,last.getService())) {
                add(result,last.getService(),new EvidenceSignal(last.getTimestamp(),last.getService(),
                        "METRIC",last.getName()+" timeout counter increased by "+delta,last.getId()));
            }
        }
    }

    private boolean hasIndependentFailureSignal(Map<String,List<EvidenceSignal>> result, String service) {
        return result.getOrDefault(service,List.of()).stream()
                .anyMatch(signal -> !"METRIC".equals(signal.family()));
    }
    private boolean hasText(String value) { return value!=null&&!value.isBlank(); }
}
