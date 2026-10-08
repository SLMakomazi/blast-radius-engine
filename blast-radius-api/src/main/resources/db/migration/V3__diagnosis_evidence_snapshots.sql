-- Append-only, explicitly requested diagnoses retain the exact evidence used.
CREATE TABLE incident_diagnoses (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents(id),
    evidence_version BIGINT NOT NULL,
    evidence_snapshot JSONB NOT NULL,
    diagnosis JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_incident_diagnoses_history ON incident_diagnoses(incident_id, created_at DESC);
