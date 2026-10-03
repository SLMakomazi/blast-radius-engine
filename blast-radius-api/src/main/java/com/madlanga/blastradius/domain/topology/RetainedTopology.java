package com.madlanga.blastradius.domain.topology;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Durable knowledge, not incident evidence. Times refer to observations, never read time. */
public record RetainedTopology(int version, String applicationId, String environment,
                               List<Node> nodes, List<Edge> edges) {
    public RetainedTopology {
        if (version != 1) throw new IllegalArgumentException("Unsupported topology version");
        Objects.requireNonNull(applicationId); Objects.requireNonNull(environment);
        nodes = List.copyOf(nodes); edges = List.copyOf(edges);
    }
    public static RetainedTopology empty(String applicationId, String environment) {
        return new RetainedTopology(1, applicationId, environment, List.of(), List.of());
    }
    public record Observation(Instant firstSeen, Instant lastSeen, String source, String sourceRef) {
        public Observation {
            Objects.requireNonNull(firstSeen); Objects.requireNonNull(lastSeen);
            Objects.requireNonNull(source); Objects.requireNonNull(sourceRef);
            if (firstSeen.isAfter(lastSeen)) throw new IllegalArgumentException("Invalid observation interval");
        }
        public Observation merge(Observation other) {
            Observation latest = other.lastSeen.isAfter(lastSeen) ? other : this;
            return new Observation(firstSeen.isBefore(other.firstSeen) ? firstSeen : other.firstSeen,
                    latest.lastSeen, latest.source, latest.sourceRef);
        }
    }
    public record Node(String id, ComponentType type, String technology, Map<String,String> metadata,
                       Observation observation) {
        public Node { metadata = Map.copyOf(metadata); Objects.requireNonNull(observation); }
    }
    public record Edge(String dependentId, String dependencyId, Observation observation) {
        public Edge { Objects.requireNonNull(observation); }
    }
}
