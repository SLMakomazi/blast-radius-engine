package com.madlanga.blastradius.topology.provider;

import com.madlanga.blastradius.topology.domain.DependencyTopology;

/**
 * Gets dependency topology from an external source.
 *
 * Implementations may obtain topology from MadlangaAI architecture analysis,
 * runtime discovery, cloud/service catalogs, traces, or local fixtures. The graph
 * engine does not know or care how the topology was discovered.
 */
public interface DependencyTopologyProvider {
    DependencyTopology getTopology(String applicationId, String environment);
}
