CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    currency_fraction_digits SMALLINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    captured_amount_minor BIGINT NOT NULL DEFAULT 0,
    refunded_amount_minor BIGINT NOT NULL DEFAULT 0,
    aggregate_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
