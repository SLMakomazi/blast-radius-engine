package com.madlanga.blastradius.topology.config;

import com.madlanga.blastradius.topology.model.*;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional explicit topology for a deployment profile. Empty means use trace-derived retained topology. */
@ConfigurationProperties("blast-radius.topology.configured")
public class ConfiguredTopology {
    private List<String> components = new ArrayList<>();
    private Map<String, List<String>> dependencies = new LinkedHashMap<>();
    public List<String> getComponents() { return components; }
    public void setComponents(List<String> components) { this.components = components; }
    public Map<String, List<String>> getDependencies() { return dependencies; }
    public void setDependencies(Map<String, List<String>> dependencies) { this.dependencies = dependencies; }
    public DependencyTopology build(String applicationId, String environment) {
        List<DependencyEdge> edges = new ArrayList<>();
        dependencies.forEach((dependent, targets) -> targets.forEach(target -> edges.add(new DependencyEdge(dependent, target))));
        return DependencyTopology.builder().applicationId(applicationId).environment(environment)
                .nodes(components.stream().map(id -> ComponentNode.builder().id(id).name(id).build()).toList())
                .edges(edges).build();
    }
}
