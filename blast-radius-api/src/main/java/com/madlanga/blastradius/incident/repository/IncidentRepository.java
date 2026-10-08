package com.madlanga.blastradius.incident.repository;

import com.madlanga.blastradius.incident.model.PersistedIncident;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository {
    /** Serialize creation, accumulation and resolution in one database transaction per monitored scope. */
    void lockScope(String applicationId, String environment, String origin);
    Optional<Instant> latestResolution(String applicationId, String environment, String origin);

    Optional<PersistedIncident> findActive(String applicationId, String environment, String originComponent);
    PersistedIncident save(PersistedIncident incident);
    List<PersistedIncident> findActive(String applicationId, String environment);
    Optional<PersistedIncident> findById(UUID id);
    List<PersistedIncident> find(String applicationId, String environment, String status, Instant from, Instant to);
}
