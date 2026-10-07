package com.madlanga.blastradius.topology.model;

import com.madlanga.blastradius.topology.model.ComponentNode;
import com.madlanga.blastradius.topology.model.DependencyEdge;
import com.madlanga.blastradius.topology.model.DependencyTopology;
import com.madlanga.blastradius.topology.model.GraphAnalysisResult;
import com.madlanga.blastradius.topology.model.TheoreticalImpact;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.TreeSet;

/**
 * Pure deterministic graph traversal for theoretical blast radius.
 *
 * Dependency edges point dependent -> dependency. Failure propagation therefore
 * traverses the graph in reverse: dependency -> its dependents.
 */
public final class DeterministicGraphEngine {

    public GraphAnalysisResult calculate(DependencyTopology topology, String originId) {
        Objects.requireNonNull(topology, "topology must not be null");
        if (originId == null || originId.isBlank()) {
            throw new IllegalArgumentException("originId must not be blank");
        }

        String originKey = originId.trim();
        ComponentNode origin = topology.getNode(originKey);
        if (origin == null) {
            throw new IllegalArgumentException("origin is not present in topology: " + originKey);
        }

        Map<String, TreeSet<String>> reverse = buildReverseAdjacency(topology);
        Map<String, Integer> distance = new HashMap<>();
        Map<String, List<String>> path = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();

        distance.put(originKey, 0);
        path.put(originKey, List.of(originKey));
        queue.add(originKey);

        while (!queue.isEmpty()) {
            String current = queue.remove();
            int nextDistance = distance.get(current) + 1;
            for (String dependent : reverse.getOrDefault(current, new TreeSet<>())) {
                List<String> candidatePath = append(path.get(current), dependent);
                Integer knownDistance = distance.get(dependent);

                if (knownDistance == null || nextDistance < knownDistance) {
                    distance.put(dependent, nextDistance);
                    path.put(dependent, candidatePath);
                    queue.add(dependent);
                } else if (nextDistance == knownDistance
                        && comparePaths(candidatePath, path.get(dependent)) < 0) {
                    path.put(dependent, candidatePath);
                    queue.add(dependent);
                }
            }
        }

        List<TheoreticalImpact> impacts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : distance.entrySet()) {
            if (entry.getKey().equals(originKey)) continue;
            impacts.add(new TheoreticalImpact(
                    topology.getNode(entry.getKey()),
                    entry.getValue(),
                    path.get(entry.getKey())));
        }

        impacts.sort(Comparator
                .comparingInt(TheoreticalImpact::getDistance)
                .thenComparing(impact -> impact.getComponent().getId()));

        return new GraphAnalysisResult(origin, impacts);
    }

    private Map<String, TreeSet<String>> buildReverseAdjacency(DependencyTopology topology) {
        Map<String, TreeSet<String>> reverse = new HashMap<>();
        for (DependencyEdge edge : topology.getEdges()) {
            reverse.computeIfAbsent(edge.getDependencyId(), ignored -> new TreeSet<>())
                    .add(edge.getDependentId());
        }
        return reverse;
    }

    private List<String> append(List<String> existing, String value) {
        List<String> result = new ArrayList<>(existing);
        result.add(value);
        return List.copyOf(result);
    }

    private int comparePaths(List<String> left, List<String> right) {
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            int compared = left.get(i).compareTo(right.get(i));
            if (compared != 0) return compared;
        }
        return Integer.compare(left.size(), right.size());
    }
}
