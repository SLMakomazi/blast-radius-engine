package com.madlanga.blastradius.ports;

import com.madlanga.blastradius.domain.incident.PersistedIncident;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository {
    Optional<PersistedIncident> findActive(String applicationId, String environment, String originComponent);
    PersistedIncident save(PersistedIncident incident);
    List<PersistedIncident> findActive(String applicationId, String environment);
    Optional<PersistedIncident> findById(UUID id);
    List<PersistedIncident> find(String applicationId, String environment, String status, Instant from, Instant to);
}
