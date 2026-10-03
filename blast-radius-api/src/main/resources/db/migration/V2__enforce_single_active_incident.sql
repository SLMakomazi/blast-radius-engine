CREATE UNIQUE INDEX uq_incidents_active_origin
    ON incidents (application_id, environment, origin_component)
    WHERE status = 'ACTIVE';
