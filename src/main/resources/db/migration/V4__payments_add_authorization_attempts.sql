ALTER TABLE payment_intents
    ADD COLUMN psp_attempt_id UUID;

CREATE TABLE payment_intent_psp_attempts (
    id UUID PRIMARY KEY,
    payment_intent_id UUID NOT NULL REFERENCES payment_intents (id),
    psp_attempt_id UUID NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(255),
    reason_code VARCHAR(255),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payment_intent_psp_attempts_outcome_check
        CHECK (outcome IN ('APPROVED', 'DECLINED', 'UNKNOWN', 'RETRYABLE_FAILURE', 'PERMANENT_FAILURE'))
);

CREATE INDEX payment_intent_psp_attempts_payment_intent_id_idx
    ON payment_intent_psp_attempts (payment_intent_id);
