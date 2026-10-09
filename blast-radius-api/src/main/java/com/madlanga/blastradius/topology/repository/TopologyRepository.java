package com.madlanga.blastradius.topology.repository;

import com.madlanga.blastradius.topology.model.RetainedTopology;

/** Replaceable storage boundary. Snapshots are isolated by application and environment. */
public interface TopologyRepository {
    default <T> T inScope(String applicationId, String environment, java.util.function.Supplier<T> operation) {
        return operation.get();
    }

    RetainedTopology load(String applicationId, String environment);
    void save(RetainedTopology topology);
}
