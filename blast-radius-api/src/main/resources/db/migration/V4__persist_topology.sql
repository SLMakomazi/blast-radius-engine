CREATE TABLE retained_topologies (
    application_id VARCHAR(255) NOT NULL,
    environment VARCHAR(255) NOT NULL,
    snapshot JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (application_id, environment),
    CONSTRAINT retained_topology_scope CHECK (
        snapshot->>'applicationId' IS NOT NULL
        AND snapshot->>'environment' IS NOT NULL
        AND snapshot->>'applicationId' = application_id
        AND snapshot->>'environment' = environment
    )
);
