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
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (amount_minor > 0),
    CHECK (currency_fraction_digits BETWEEN 0 AND 3),
    CHECK (captured_amount_minor >= 0 AND captured_amount_minor <= amount_minor),
    CHECK (refunded_amount_minor >= 0 AND refunded_amount_minor <= captured_amount_minor),
    CHECK (aggregate_version >= 0),
    CHECK (updated_at >= created_at)
);

CREATE INDEX payment_intents_merchant_id_id_idx
    ON payment_intents (merchant_id, id);
