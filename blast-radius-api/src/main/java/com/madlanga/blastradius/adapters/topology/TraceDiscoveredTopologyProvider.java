package com.madlanga.blastradius.adapters.topology;

import com.madlanga.blastradius.domain.evidence.SpanEvidence;
import com.madlanga.blastradius.domain.evidence.TelemetryBundle;
import com.madlanga.blastradius.domain.evidence.TelemetryQuery;
import com.madlanga.blastradius.domain.topology.ComponentNode;
import com.madlanga.blastradius.domain.topology.ComponentType;
import com.madlanga.blastradius.domain.topology.DependencyEdge;
import com.madlanga.blastradius.domain.topology.DependencyTopology;
import com.madlanga.blastradius.ports.DependencyTopologyProvider;
import com.madlanga.blastradius.ports.TelemetryProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Local-lab topology discovery from normalized distributed spans.
 *
 * This is an adapter, not graph logic. It proves that Phase 5 topology can be
 * discovered automatically from runtime evidence instead of hand-authored for
 * every monitored system. MadlangaAI can later provide its architecture model
 * through the same DependencyTopologyProvider port.
 */
public final class TraceDiscoveredTopologyProvider implements DependencyTopologyProvider {

    private static final Duration DEFAULT_LOOKBACK = Duration.ofMinutes(15);

    private final TelemetryProvider telemetryProvider;
    private final Clock clock;
    private final Duration lookback;

    public TraceDiscoveredTopologyProvider(TelemetryProvider telemetryProvider) {
        this(telemetryProvider, Clock.systemUTC(), DEFAULT_LOOKBACK);
    }

    TraceDiscoveredTopologyProvider(TelemetryProvider telemetryProvider, Clock clock, Duration lookback) {
        this.telemetryProvider = telemetryProvider;
        this.clock = clock;
        this.lookback = lookback;
    }

    @Override
    public DependencyTopology getTopology(String applicationId, String environment) {
        Instant to = clock.instant();
        TelemetryBundle telemetry = telemetryProvider.getTelemetry(TelemetryQuery.builder()
                .applicationId(applicationId)
                .environment(environment)
                .from(to.minus(lookback))
                .to(to)
                .build());

        return discover(applicationId, environment, telemetry.getSpans());
    }

    public DependencyTopology discover(String applicationId, String environment, List<SpanEvidence> spans) {
        Map<String, SpanEvidence> bySpanId = new HashMap<>();
        for (SpanEvidence span : spans) {
            if (hasText(span.getSpanId())) bySpanId.put(span.getSpanId(), span);
        }

        Set<String> components = new LinkedHashSet<>();
        Set<DependencyEdge> edges = new LinkedHashSet<>();

        for (SpanEvidence span : spans) {
            String service = normalized(span.getService());
            if (service != null && !"unknown".equalsIgnoreCase(service)) {
                components.add(service);
            }

            String parentId = normalized(span.getParentSpanId());
            if (service != null && parentId != null) {
                SpanEvidence parent = bySpanId.get(parentId);
                if (parent != null) {
                    String parentService = normalized(parent.getService());
                    if (parentService != null && !parentService.equals(service)) {
                        components.add(parentService);
                        edges.add(new DependencyEdge(parentService, service));
                    }
                }
            }

            String peer = normalized(span.getPeerService());
            if (service != null && peer != null && !peer.equals(service)) {
                components.add(peer);
                edges.add(new DependencyEdge(service, peer));
            }
        }

        List<ComponentNode> nodes = components.stream()
                .sorted()
                .map(id -> ComponentNode.builder()
                        .id(id)
                        .name(id)
                        .type(inferType(id, spans))
                        .technology(inferTechnology(id, spans))
                        .build())
                .toList();

        List<DependencyEdge> sortedEdges = new ArrayList<>(edges);
        sortedEdges.sort(Comparator.comparing(DependencyEdge::getDependentId)
                .thenComparing(DependencyEdge::getDependencyId));

        return DependencyTopology.builder()
                .applicationId(applicationId)
                .environment(environment)
                .nodes(nodes)
                .edges(sortedEdges)
                .build();
    }

    private ComponentType inferType(String componentId, List<SpanEvidence> spans) {
        for (SpanEvidence span : spans) {
            if (componentId.equals(normalized(span.getPeerService()))) {
                String dbSystem = firstNonBlank(span.getAttributes().get("db.system.name"),
                        span.getAttributes().get("db.system"));
                if (dbSystem != null) return ComponentType.DATABASE;
            }
        }
        return ComponentType.SERVICE;
    }

    private String inferTechnology(String componentId, List<SpanEvidence> spans) {
        for (SpanEvidence span : spans) {
            if (componentId.equals(normalized(span.getPeerService()))) {
                String dbSystem = firstNonBlank(span.getAttributes().get("db.system.name"),
                        span.getAttributes().get("db.system"));
                if (dbSystem != null) return dbSystem.toUpperCase(java.util.Locale.ROOT);
            }
        }
        return null;
    }

    private static String normalized(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String first, String second) {
        return hasText(first) ? first : (hasText(second) ? second : null);
    }
}
