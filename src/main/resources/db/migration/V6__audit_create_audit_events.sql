CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    reason TEXT,
    metadata TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT audit_events_actor_type_check CHECK (actor_type IN ('MERCHANT', 'OPERATOR', 'SYSTEM'))
);

CREATE INDEX audit_events_resource_idx
    ON audit_events (resource_type, resource_id, occurred_at, id);

CREATE FUNCTION reject_audit_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit events are append-only and cannot be updated or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
