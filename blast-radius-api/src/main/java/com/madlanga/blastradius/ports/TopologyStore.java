package com.madlanga.blastradius.ports;

import com.madlanga.blastradius.domain.topology.RetainedTopology;

/** Replaceable storage boundary. Local implementation supports one API writer. */
public interface TopologyStore {
    RetainedTopology load(String applicationId, String environment);
    void save(RetainedTopology topology);
}
