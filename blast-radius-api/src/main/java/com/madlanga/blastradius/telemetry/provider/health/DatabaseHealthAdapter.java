package com.madlanga.blastradius.telemetry.provider.health;

import com.madlanga.blastradius.telemetry.config.TelemetryConfig.DatabaseHealthProperties;
import com.madlanga.blastradius.telemetry.model.EvidenceFamily;
import com.madlanga.blastradius.telemetry.model.EvidenceProvenance;
import com.madlanga.blastradius.telemetry.model.HealthEvidence;
import com.madlanga.blastradius.telemetry.model.HealthState;
import com.madlanga.blastradius.telemetry.model.TelemetryQuery;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Direct availability probe for the monitored PostgreSQL dependency. */
@Component
public class DatabaseHealthAdapter {
    private final DatabaseHealthProperties properties;

    public DatabaseHealthAdapter(DatabaseHealthProperties properties) {
        this.properties = properties;
    }

    public HealthEvidence fetchHealth(TelemetryQuery query) {
        if (query.hasComponentFilter() && !query.getComponentFilter().contains(properties.getService())) return null;
        Instant timestamp = query.getTo().minusNanos(1);
        long started = System.nanoTime();
        HealthState state;
        try {
            DriverManager.setLoginTimeout((int) Math.max(1, properties.getTimeout().toSeconds()));
            try (Connection connection = DriverManager.getConnection(
                    properties.getUrl(), properties.getUsername(), properties.getPassword())) {
                state = connection.isValid((int) Math.max(1, properties.getTimeout().toSeconds()))
                        ? HealthState.UP : HealthState.DOWN;
            }
        } catch (Exception ignored) {
            state = HealthState.DOWN;
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        EvidenceProvenance provenance = EvidenceProvenance.of(
                EvidenceFamily.HEALTH, properties.getProviderId(), timestamp, "jdbc:postgresql");
        return HealthEvidence.builder()
                .id("health-" + UUID.randomUUID())
                .timestamp(timestamp)
                .service(properties.getService())
                .environment(query.getEnvironment())
                .endpoint("database/availability")
                .state(state)
                .httpStatus(null)
                .latencyMs(latencyMs)
                .details(Map.of())
                .provenance(provenance)
                .build();
    }
}
