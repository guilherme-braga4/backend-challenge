CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE policies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    max_per_payment NUMERIC(19,2) NULL,
    daytime_daily_limit NUMERIC(19,2) NULL,
    nighttime_daily_limit NUMERIC(19,2) NULL,
    weekend_daily_limit NUMERIC(19,2) NULL,
    max_tx_per_day INT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_name VARCHAR(255) NOT NULL,
    policy_id UUID NULL REFERENCES policies(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    amount NUMERIC(19,2) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_payments_wallet_occurred ON payments(wallet_id, occurred_at DESC);
CREATE INDEX idx_payments_idempotency ON payments(idempotency_key);
CREATE INDEX idx_payments_id_desc ON payments(id DESC);
