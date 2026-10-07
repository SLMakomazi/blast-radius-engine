package com.madlanga.blastradius.topology.service;

import com.madlanga.blastradius.telemetry.model.*;
import com.madlanga.blastradius.topology.domain.*;
import com.madlanga.blastradius.topology.domain.RetainedTopology.*;
import com.madlanga.blastradius.topology.application.port.DependencyTopologyProvider;
import com.madlanga.blastradius.topology.application.port.RuntimeSpanProvider;
import com.madlanga.blastradius.topology.application.port.TopologyRepository;
import java.time.*;
import java.util.*;

/** Automatic runtime discovery merged with durable knowledge; never manufactures incident evidence. */
public final class TopologyService implements DependencyTopologyProvider {
    private final TopologyRepository store;
    private final RuntimeSpanProvider source;
    private final Clock clock;
    private final Duration ttl;
    private final Duration lookback;
    private final TraceTopologyService discovery = new TraceTopologyService(null);

    public TopologyService(TopologyRepository store, RuntimeSpanProvider source, Clock clock,
                                    Duration ttl, Duration lookback) {
        if (ttl.isNegative() || ttl.isZero() || lookback.isNegative() || lookback.isZero())
            throw new IllegalArgumentException("Topology durations must be positive");
        this.store = store; this.source = source; this.clock = clock; this.ttl = ttl; this.lookback = lookback;
    }

    @Override public synchronized DependencyTopology getTopology(String applicationId, String environment) {
        Instant now = clock.instant();
        List<SpanEvidence> spans = source.getSpans(TelemetryQuery.builder()
                .applicationId(applicationId).environment(environment).from(now.minus(lookback)).to(now).build());
        return learn(applicationId, environment, spans);
    }

    /** Also used by offline acceptance bootstrap; the same validation and observation timestamps apply. */
    public synchronized DependencyTopology learn(String applicationId, String environment, List<SpanEvidence> spans) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(ttl);
        RetainedTopology previous = store.load(applicationId, environment);
        Map<String,Node> nodes = new TreeMap<>();
        Map<DependencyEdge,Edge> edges = new LinkedHashMap<>();
        previous.nodes().stream().filter(n -> n.observation().lastSeen().isAfter(cutoff))
                .forEach(n -> nodes.put(n.id(), n));
        previous.edges().stream().filter(e -> e.observation().lastSeen().isAfter(cutoff))
                .filter(e -> nodes.containsKey(e.dependentId()) && nodes.containsKey(e.dependencyId()))
                .forEach(e -> edges.put(new DependencyEdge(e.dependentId(), e.dependencyId()), e));
        List<SpanEvidence> valid = spans.stream()
                .filter(s -> environment.equals(s.getEnvironment()))
                .filter(s -> !s.getStartTime().isAfter(now) && s.getStartTime().isAfter(cutoff)).toList();
        Map<String,SpanEvidence> byId = new HashMap<>();
        valid.forEach(s -> byId.put(key(s.getTraceId(), s.getSpanId()), s));
        for (SpanEvidence span : valid) {
            // A relationship's freshness belongs to the child/client observation, not a nearby service heartbeat.
            List<SpanEvidence> context = new ArrayList<>(); context.add(span);
            SpanEvidence parent = byId.get(key(span.getTraceId(), span.getParentSpanId()));
            if (parent != null) context.add(parent);
            DependencyTopology fragment = discovery.discover(applicationId, environment, context);
            Observation observed = new Observation(span.getStartTime(), span.getStartTime(),
                    span.getProvenance().getProvider(), span.getProvenance().getSourceRef());
            // Only the emitting service and explicit peer are refreshed by this span.
            for (ComponentNode node : fragment.getNodes()) {
                if (!node.getId().equals(span.getService()) && !node.getId().equals(span.getPeerService())) continue;
                Node next = new Node(node.getId(), node.getType(), node.getTechnology(), node.getMetadata(), observed);
                nodes.merge(node.getId(), next, TopologyService::mergeNode);
            }
            for (DependencyEdge edge : fragment.getEdges()) {
                boolean explicit = edge.getDependentId().equals(span.getService())
                        && edge.getDependencyId().equals(span.getPeerService());
                boolean crossService = parent != null && edge.getDependentId().equals(parent.getService())
                        && edge.getDependencyId().equals(span.getService());
                if (!explicit && !crossService) continue;
                // Cross-service parent is a real observed endpoint too.
                ComponentNode dependent = fragment.getNode(edge.getDependentId());
                nodes.putIfAbsent(dependent.getId(), new Node(dependent.getId(), dependent.getType(),
                        dependent.getTechnology(), dependent.getMetadata(), observed));
                Edge next = new Edge(edge.getDependentId(), edge.getDependencyId(), observed);
                edges.merge(edge, next, (a,b) -> new Edge(a.dependentId(), a.dependencyId(), a.observation().merge(b.observation())));
            }
        }
        List<Edge> sortedEdges = edges.values().stream().sorted(Comparator.comparing(Edge::dependentId)
                .thenComparing(Edge::dependencyId)).toList();
        RetainedTopology updated = new RetainedTopology(1, applicationId, environment, List.copyOf(nodes.values()), sortedEdges);
        if (!updated.equals(previous)) store.save(updated);
        return toTopology(updated);
    }

    private static Node mergeNode(Node a, Node b) {
        boolean newer = !b.observation().lastSeen().isBefore(a.observation().lastSeen());
        ComponentType type = b.type() == ComponentType.UNKNOWN || b.type() == ComponentType.SERVICE
                ? a.type() : (newer || a.type() == ComponentType.UNKNOWN || a.type() == ComponentType.SERVICE ? b.type() : a.type());
        String technology = b.technology() != null && (newer || a.technology() == null) ? b.technology() : a.technology();
        Map<String,String> metadata = new LinkedHashMap<>(a.metadata()); metadata.putAll(b.metadata());
        return new Node(a.id(), type, technology, metadata, a.observation().merge(b.observation()));
    }

    private static DependencyTopology toTopology(RetainedTopology retained) {
        return DependencyTopology.builder().applicationId(retained.applicationId()).environment(retained.environment())
                .nodes(retained.nodes().stream().map(n -> {
                    Map<String,String> metadata = new LinkedHashMap<>(n.metadata());
                    metadata.put("firstSeen", n.observation().firstSeen().toString());
                    metadata.put("lastSeen", n.observation().lastSeen().toString());
                    metadata.put("source", n.observation().source());
                    return ComponentNode.builder().id(n.id()).name(n.id()).type(n.type())
                            .technology(n.technology()).metadata(metadata).build();
                }).toList())
                .edges(retained.edges().stream().map(e -> new DependencyEdge(e.dependentId(), e.dependencyId())).toList()).build();
    }
    private static String key(String traceId, String spanId) { return traceId + ":" + spanId; }
}
