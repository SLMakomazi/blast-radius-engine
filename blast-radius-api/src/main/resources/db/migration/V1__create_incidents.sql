CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    application_id VARCHAR(200) NOT NULL,
    environment VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'RESOLVED')),
    started_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    origin_component VARCHAR(200),
    origin_confidence VARCHAR(20),
    severity_level VARCHAR(20) NOT NULL,
    severity_score INTEGER NOT NULL CHECK (severity_score >= 0 AND severity_score <= 100),
    analysis_from TIMESTAMPTZ NOT NULL,
    analysis_to TIMESTAMPTZ NOT NULL,
    analysis_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT incidents_resolution_consistency CHECK (
        (status = 'ACTIVE' AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND resolved_at IS NOT NULL)
    )
);

CREATE INDEX idx_incidents_app_env_started
    ON incidents (application_id, environment, started_at DESC);

CREATE INDEX idx_incidents_status_started
    ON incidents (status, started_at DESC);
