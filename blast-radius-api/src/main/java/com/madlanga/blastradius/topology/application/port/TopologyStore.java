package com.madlanga.blastradius.topology.application.port;

import com.madlanga.blastradius.topology.domain.RetainedTopology;

/** Replaceable storage boundary. Local implementation supports one API writer. */
public interface TopologyStore {
    RetainedTopology load(String applicationId, String environment);
    void save(RetainedTopology topology);
}
