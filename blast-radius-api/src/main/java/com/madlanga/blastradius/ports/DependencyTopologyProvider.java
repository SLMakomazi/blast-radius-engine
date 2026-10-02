package com.madlanga.blastradius.ports;

import com.madlanga.blastradius.domain.topology.DependencyTopology;

/**
 * Provider-neutral source of dependency topology.
 *
 * Implementations may obtain topology from MadlangaAI architecture analysis,
 * runtime discovery, cloud/service catalogs, traces, or local fixtures. The graph
 * engine does not know or care how the topology was discovered.
 */
public interface DependencyTopologyProvider {
    DependencyTopology getTopology(String applicationId, String environment);
}
