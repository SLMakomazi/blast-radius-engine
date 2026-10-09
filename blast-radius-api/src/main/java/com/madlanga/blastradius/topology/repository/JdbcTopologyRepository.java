package com.madlanga.blastradius.topology.repository;

import com.madlanga.blastradius.topology.model.RetainedTopology;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Durable topology snapshots scoped to a monitored application and environment. */
public final class JdbcTopologyRepository implements TopologyRepository {
    private final JdbcTemplate jdbc;
    private final org.springframework.transaction.support.TransactionTemplate transaction;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public JdbcTopologyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                        java.util.Objects.requireNonNull(jdbc.getDataSource())));
    }

    @Override
    public <T> T inScope(String applicationId, String environment, java.util.function.Supplier<T> operation) {
        return transaction.execute(status -> {
            String scope = applicationId.length() + ":" + applicationId + environment.length() + ":" + environment;
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, scope);
            return operation.get();
        });
    }

    @Override
    public RetainedTopology load(String applicationId, String environment) {
        return jdbc.query("""
                SELECT snapshot FROM retained_topologies WHERE application_id = ? AND environment = ?
                """, (rs, row) -> {
            RetainedTopology topology = mapper.readValue(rs.getString("snapshot"), RetainedTopology.class);
            if (!applicationId.equals(topology.applicationId()) || !environment.equals(topology.environment())) {
                throw new IllegalStateException("Topology scope mismatch");
            }
            return topology;
        }, applicationId, environment).stream().findFirst()
                .orElseGet(() -> RetainedTopology.empty(applicationId, environment));
    }

    @Override
    public void save(RetainedTopology topology) {
        jdbc.update("""
                INSERT INTO retained_topologies (application_id, environment, snapshot)
                VALUES (?, ?, CAST(? AS jsonb))
                ON CONFLICT (application_id, environment) DO UPDATE
                SET snapshot = EXCLUDED.snapshot, updated_at = CURRENT_TIMESTAMP
                """, topology.applicationId(), topology.environment(), mapper.writeValueAsString(topology));
    }
}
