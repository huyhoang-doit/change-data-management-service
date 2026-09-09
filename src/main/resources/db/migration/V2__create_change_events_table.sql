-- V2: Create change_events table
-- Core table for exactly-once processing guarantee
-- UNIQUE(event_id) is the DB-level safeguard against duplicate processing

CREATE TABLE IF NOT EXISTS change_events
(
    -- CDMS internal identity
    id           BIGSERIAL PRIMARY KEY,

    -- Deduplication key — THE most important constraint in this system
    -- DB UNIQUE guarantees exactly-once even under concurrent requests
    event_id     VARCHAR(200) NOT NULL,

    -- Linked product
    product_id   VARCHAR(100),            -- external product ID (Vietful)
    sku          VARCHAR(100),            -- product SKU for quick reference

    -- Ingestion source
    source       VARCHAR(50),            -- WEBHOOK | SCHEDULER | EXCEL

    -- Processing outcome
    status       VARCHAR(20),            -- PROCESSED | DUPLICATE | OLD_DATA | FAILED

    -- Full event payload (stored for audit/replay)
    payload      TEXT,                   -- raw JSON payload from source
    payload_hash VARCHAR(64),            -- SHA-256 of payload for change detection

    -- Timestamps
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,              -- when processing completed

    -- Uniqueness: one record per event_id — DB-level exactly-once guarantee
    CONSTRAINT uq_change_events_event_id UNIQUE (event_id)
);

-- Fast lookup by event_id (most common query — deduplication check)
CREATE INDEX IF NOT EXISTS idx_change_events_event_id
    ON change_events (event_id);

-- Range queries for monitoring & reporting
CREATE INDEX IF NOT EXISTS idx_change_events_created_at
    ON change_events (created_at);

-- Filter by status for monitoring dashboards
CREATE INDEX IF NOT EXISTS idx_change_events_status
    ON change_events (status);

-- Filter by source for ingestion analytics
CREATE INDEX IF NOT EXISTS idx_change_events_source
    ON change_events (source);
