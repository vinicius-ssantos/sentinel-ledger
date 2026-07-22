CREATE TABLE reconciliation_cases (
    id UUID PRIMARY KEY,
    payment_intent_id UUID NOT NULL,
    fingerprint VARCHAR(512) NOT NULL,
    mismatch_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    local_evidence VARCHAR(512) NOT NULL,
    provider_evidence VARCHAR(512) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    resolved_by_operator_id UUID,
    resolution_reason TEXT,
    resolution_action VARCHAR(32),
    compensating_transaction_reference VARCHAR(255),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT reconciliation_cases_status_check
        CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'IGNORED_WITH_REASON')),
    CONSTRAINT reconciliation_cases_severity_check
        CHECK (severity IN ('LOW', 'HIGH')),
    CONSTRAINT reconciliation_cases_resolution_action_check
        CHECK (resolution_action IS NULL OR resolution_action IN ('ACKNOWLEDGE_NO_ACTION', 'COMPENSATE', 'IGNORE')),
    CONSTRAINT reconciliation_cases_resolution_consistency_check CHECK (
        (
            status IN ('RESOLVED', 'IGNORED_WITH_REASON')
            AND resolved_by_operator_id IS NOT NULL
            AND resolution_reason IS NOT NULL
            AND resolution_action IS NOT NULL
            AND resolved_at IS NOT NULL
        ) OR (
            status IN ('OPEN', 'INVESTIGATING')
            AND resolved_by_operator_id IS NULL
            AND resolution_reason IS NULL
            AND resolution_action IS NULL
            AND resolved_at IS NULL
        )
    )
);

CREATE UNIQUE INDEX reconciliation_cases_open_fingerprint_key
    ON reconciliation_cases (fingerprint)
    WHERE status IN ('OPEN', 'INVESTIGATING');

CREATE INDEX reconciliation_cases_payment_intent_id_idx
    ON reconciliation_cases (payment_intent_id);

CREATE INDEX reconciliation_cases_status_detected_at_idx
    ON reconciliation_cases (status, detected_at);

CREATE FUNCTION protect_reconciliation_case_evidence() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'reconciliation cases cannot be deleted';
    END IF;
    IF OLD.payment_intent_id IS DISTINCT FROM NEW.payment_intent_id
        OR OLD.fingerprint IS DISTINCT FROM NEW.fingerprint
        OR OLD.mismatch_type IS DISTINCT FROM NEW.mismatch_type
        OR OLD.local_evidence IS DISTINCT FROM NEW.local_evidence
        OR OLD.provider_evidence IS DISTINCT FROM NEW.provider_evidence
        OR OLD.detected_at IS DISTINCT FROM NEW.detected_at THEN
        RAISE EXCEPTION 'reconciliation case evidence is immutable once detected';
    END IF;
    IF OLD.status IN ('RESOLVED', 'IGNORED_WITH_REASON') THEN
        RAISE EXCEPTION 'a resolved reconciliation case cannot be modified further';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reconciliation_cases_protect_evidence
    BEFORE UPDATE OR DELETE ON reconciliation_cases
    FOR EACH ROW EXECUTE FUNCTION protect_reconciliation_case_evidence();
