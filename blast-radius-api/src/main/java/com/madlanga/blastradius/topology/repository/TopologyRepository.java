package com.madlanga.blastradius.topology.repository;

import com.madlanga.blastradius.topology.domain.RetainedTopology;

/** Replaceable storage boundary. Local implementation supports one API writer. */
public interface TopologyRepository {
    RetainedTopology load(String applicationId, String environment);
    void save(RetainedTopology topology);
}
