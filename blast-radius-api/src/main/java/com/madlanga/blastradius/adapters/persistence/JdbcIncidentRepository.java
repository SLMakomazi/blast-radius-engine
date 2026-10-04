package com.madlanga.blastradius.adapters.persistence;

import com.madlanga.blastradius.domain.incident.*;
import com.madlanga.blastradius.ports.IncidentRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIncidentRepository implements IncidentRepository {
    private final JdbcTemplate jdbc;

    public JdbcIncidentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PersistedIncident> findActive(String applicationId, String environment, String originComponent) {
        List<PersistedIncident> rows = jdbc.query("""
                SELECT * FROM incidents
                WHERE application_id = ? AND environment = ? AND origin_component = ? AND status = 'ACTIVE'
                ORDER BY started_at DESC
                LIMIT 1
                """, this::map, applicationId, environment, originComponent);
        return rows.stream().findFirst();
    }

    @Override
    public List<PersistedIncident> findActive(String applicationId, String environment) {
        return jdbc.query("""
                SELECT * FROM incidents
                WHERE application_id = ? AND environment = ? AND status = 'ACTIVE'
                ORDER BY started_at DESC
                """, this::map, applicationId, environment);
    }

    @Override
    public PersistedIncident save(PersistedIncident incident) {
        int updated = jdbc.update("""
                INSERT INTO incidents (
                    id, application_id, environment, status, started_at, resolved_at,
                    origin_component, origin_confidence, severity_level, severity_score,
                    analysis_from, analysis_to, analysis_snapshot, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    resolved_at = EXCLUDED.resolved_at,
                    origin_confidence = EXCLUDED.origin_confidence,
                    severity_level = EXCLUDED.severity_level,
                    severity_score = EXCLUDED.severity_score,
                    analysis_from = EXCLUDED.analysis_from,
                    analysis_to = EXCLUDED.analysis_to,
                    analysis_snapshot = EXCLUDED.analysis_snapshot,
                    updated_at = EXCLUDED.updated_at
                """,
                incident.id(), incident.applicationId(), incident.environment(), incident.status().name(),
                ts(incident.startedAt()), ts(incident.resolvedAt()), incident.originComponent(),
                incident.originConfidence() == null ? null : incident.originConfidence().name(),
                incident.severityLevel().name(), incident.severityScore(), ts(incident.analysisFrom()),
                ts(incident.analysisTo()), incident.analysisSnapshot(), ts(incident.createdAt()), ts(incident.updatedAt()));
        if (updated != 1) throw new IllegalStateException("incident persistence did not affect exactly one row");
        return findById(incident.id()).orElseThrow(() -> new IllegalStateException("persisted incident could not be reloaded"));
    }

    @Override
    public Optional<PersistedIncident> findById(UUID id) {
        return jdbc.query("SELECT * FROM incidents WHERE id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public List<PersistedIncident> find(String applicationId, String environment, String status, Instant from, Instant to) {
        StringBuilder sql = new StringBuilder("SELECT * FROM incidents WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (hasText(applicationId)) { sql.append(" AND application_id = ?"); args.add(applicationId.trim()); }
        if (hasText(environment)) { sql.append(" AND environment = ?"); args.add(environment.trim()); }
        if (hasText(status)) { sql.append(" AND status = ?"); args.add(status.trim().toUpperCase(Locale.ROOT)); }
        if (from != null) { sql.append(" AND started_at >= ?"); args.add(ts(from)); }
        if (to != null) { sql.append(" AND started_at < ?"); args.add(ts(to)); }
        sql.append(" ORDER BY started_at DESC");
        return jdbc.query(sql.toString(), this::map, args.toArray());
    }

    private PersistedIncident map(ResultSet rs, int rowNum) throws SQLException {
        String confidence = rs.getString("origin_confidence");
        return new PersistedIncident(
                rs.getObject("id", UUID.class),
                rs.getString("application_id"),
                rs.getString("environment"),
                IncidentStatus.valueOf(rs.getString("status")),
                instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("resolved_at")),
                rs.getString("origin_component"),
                confidence == null ? null : ConfidenceLevel.valueOf(confidence),
                SeverityLevel.valueOf(rs.getString("severity_level")),
                rs.getInt("severity_score"),
                instant(rs.getTimestamp("analysis_from")),
                instant(rs.getTimestamp("analysis_to")),
                rs.getString("analysis_snapshot"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
