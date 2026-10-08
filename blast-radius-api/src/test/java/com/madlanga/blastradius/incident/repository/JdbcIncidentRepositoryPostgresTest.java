package com.madlanga.blastradius.incident.repository;

import com.madlanga.blastradius.incident.model.IncidentSeverity;
import com.madlanga.blastradius.incident.model.OriginAssessment;
import com.madlanga.blastradius.incident.model.PersistedIncident;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * Uses only the isolated PostgreSQL instance provisioned by CI_PG_URL.
 * Never touches a developer's database or resets existing incident history.
 */
@EnabledIfEnvironmentVariable(named = "CI_PG_URL", matches = ".+")
class JdbcIncidentRepositoryPostgresTest {

    @Test
    void migrationsAdvisoryLocksJsonbUniquenessAndDiagnosisArchiveWorkOnPostgres() {
        String url = System.getenv("CI_PG_URL");
        String user = System.getenv("CI_PG_USER");
        String password = System.getenv("CI_PG_PASSWORD");
        var dataSource = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration").load().migrate();

        var jdbc = new JdbcTemplate(dataSource);
        var repository = new JdbcIncidentRepository(jdbc);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        String application = "ci-" + UUID.randomUUID();
        String environment = "integration";
        UUID firstId = UUID.randomUUID();
        Instant started = Instant.now().minusSeconds(40).truncatedTo(ChronoUnit.MILLIS);

        try {
            var first = new PersistedIncident(firstId, application, environment,
                    PersistedIncident.Status.ACTIVE, started, null, "postgres",
                    OriginAssessment.Confidence.HIGH, IncidentSeverity.Level.HIGH, 80,
                    started, started.plusSeconds(10),
                    "{\"timeline\":[],\"incidentType\":\"OUTAGE\"}", started, started);
            var stored = transaction.execute(status -> {
                repository.lockScope(application, environment, "postgres");
                return repository.save(first);
            });
            assertThat(stored).isNotNull();
            assertThat(stored.id()).isEqualTo(firstId);
            assertThat(repository.findActive(application, environment, "postgres")).isPresent();
            assertThat(repository.findById(firstId).orElseThrow().analysisSnapshot())
                    .contains("\"incidentType\"");

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO incidents(id,application_id,environment,status,started_at,
                        origin_component,severity_level,severity_score,analysis_from,analysis_to,analysis_snapshot)
                    VALUES (?,?,?,'ACTIVE',?,?,?,?,?,?,CAST(? AS jsonb))
                    """, UUID.randomUUID(), application, environment, java.sql.Timestamp.from(started), "postgres",
                    "HIGH", 80, java.sql.Timestamp.from(started), java.sql.Timestamp.from(started.plusSeconds(10)), "{}"))
                    .isInstanceOf(DataIntegrityViolationException.class);

            UUID diagnosisId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO incident_diagnoses(id,incident_id,evidence_version,evidence_snapshot,diagnosis)
                    VALUES (?,?,?,CAST(? AS jsonb),CAST(? AS jsonb))
                    """, diagnosisId, firstId, 1L,
                    "{\"timeline\":[]}", "{\"summary\":\"isolated integration evidence\"}");
            assertThat(jdbc.queryForObject(
                    "SELECT evidence_version FROM incident_diagnoses WHERE id=?", Long.class, diagnosisId))
                    .isEqualTo(1L);

            var resolved = new PersistedIncident(firstId, application, environment,
                    PersistedIncident.Status.RESOLVED, started, started.plusSeconds(20),
                    "postgres", OriginAssessment.Confidence.HIGH, IncidentSeverity.Level.HIGH, 80,
                    started, started.plusSeconds(10), first.analysisSnapshot(), started, started.plusSeconds(20));
            transaction.execute(status -> {
                repository.lockScope(application, environment, "postgres");
                return repository.save(resolved);
            });
            assertThat(repository.findById(firstId).orElseThrow().status())
                    .isEqualTo(PersistedIncident.Status.RESOLVED);

            UUID newIncidentId = UUID.randomUUID();
            var subsequent = new PersistedIncident(newIncidentId, application, environment,
                    PersistedIncident.Status.ACTIVE, started.plusSeconds(30), null,
                    "postgres", OriginAssessment.Confidence.HIGH, IncidentSeverity.Level.HIGH, 80,
                    started.plusSeconds(30), started.plusSeconds(40), "{\"timeline\":[]}",
                    started.plusSeconds(30), started.plusSeconds(30));
            transaction.execute(status -> {
                repository.lockScope(application, environment, "postgres");
                return repository.save(subsequent);
            });
            assertThat(repository.findActive(application, environment, "postgres").orElseThrow().id())
                    .isEqualTo(newIncidentId);
        } finally {
            jdbc.update("""
                    DELETE FROM incident_diagnoses
                    WHERE incident_id IN (SELECT id FROM incidents WHERE application_id=?)
                    """, application);
            jdbc.update("DELETE FROM incidents WHERE application_id=?", application);
        }
    }
}
