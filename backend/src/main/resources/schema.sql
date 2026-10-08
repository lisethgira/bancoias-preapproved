CREATE TABLE IF NOT EXISTS pre_approved (
    id               VARCHAR(20)    PRIMARY KEY,
    customer_id      VARCHAR(20)    NOT NULL,
    status           VARCHAR(10)    NOT NULL,
    available_amount NUMERIC(15,2)  NOT NULL CHECK (available_amount >= 0)
);

CREATE TABLE IF NOT EXISTS usage_request (
    id                BIGSERIAL      PRIMARY KEY,
    request_reference VARCHAR(50)    NOT NULL UNIQUE,
    pre_approved_id   VARCHAR(20)    NOT NULL,
    customer_id       VARCHAR(20)    NOT NULL,
    amount            NUMERIC(15,2)  NOT NULL,
    payload_hash      VARCHAR(64)    NOT NULL,
    status            VARCHAR(12)    NOT NULL,
    rejection_reason  VARCHAR(40),
    processed_at      TIMESTAMPTZ    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_usage_request_processed_at
    ON usage_request (processed_at DESC);

-- Outbox: eventos pendientes de publicar, escritos en la misma transacción que la autorización.
CREATE TABLE IF NOT EXISTS outbox_event (
    id            UUID          PRIMARY KEY,
    aggregate_id  VARCHAR(50)   NOT NULL,
    event_type    VARCHAR(60)   NOT NULL,
    payload       TEXT          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    published_at  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox_event (created_at) WHERE published_at IS NULL;

-- Eventos ya procesados por el consumidor, para descartar duplicados.
CREATE TABLE IF NOT EXISTS processed_event (
    event_id      UUID          PRIMARY KEY,
    event_type    VARCHAR(60)   NOT NULL,
    processed_at  TIMESTAMPTZ   NOT NULL
);