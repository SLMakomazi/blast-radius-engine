package com.madlanga.blastradius.topology.repository;

import com.madlanga.blastradius.topology.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CI_PG_URL", matches = ".+")
class JdbcTopologyRepositoryPostgresTest {
    @Test
    void flywaySnapshotsSurviveRepositoryRestartAndRemainScopedAndTransactional() {
        var source = new DriverManagerDataSource(System.getenv("CI_PG_URL"),
                System.getenv("CI_PG_USER"), System.getenv("CI_PG_PASSWORD"));
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(source);
        var first = new JdbcTopologyRepository(jdbc);
        var restarted = new JdbcTopologyRepository(new JdbcTemplate(source));
        String application = "topology-test-" + UUID.randomUUID();
        var observed = new RetainedTopology.Observation(Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z"), "test", "trace-1");
        var snapshot = new RetainedTopology(1, application, "test", List.of(
                new RetainedTopology.Node("api", ComponentType.SERVICE, "JAVA", Map.of(), observed),
                new RetainedTopology.Node("db", ComponentType.DATABASE, "POSTGRESQL", Map.of(), observed)),
                List.of(new RetainedTopology.Edge("api", "db", observed)));
        try {
            assertThat(first.load(application, "test")).isEqualTo(RetainedTopology.empty(application, "test"));
            first.inScope(application, "test", () -> { first.save(snapshot); return null; });
            assertThat(restarted.load(application, "test")).isEqualTo(snapshot);
            assertThat(restarted.load(application, "other").nodes()).isEmpty();
            assertThatThrownBy(() -> restarted.inScope(application, "test", () -> {
                restarted.save(RetainedTopology.empty(application, "test"));
                throw new IllegalStateException("rollback");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(first.load(application, "test")).isEqualTo(snapshot);
            restarted.save(RetainedTopology.empty(application, "test"));
            assertThat(first.load(application, "test").nodes()).isEmpty();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM retained_topologies WHERE application_id=?",
                    Integer.class, application)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM retained_topologies WHERE application_id=?", application);
        }
    }
}
