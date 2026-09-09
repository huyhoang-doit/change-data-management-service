-- V1: Create products table
-- Maps to Vietful Inventory Product API response
-- CDMS internal fields: version, content_hash, source

CREATE TABLE IF NOT EXISTS products
(
    -- CDMS internal identity
    id           BIGSERIAL PRIMARY KEY,

    -- Vietful product identity
    external_id  VARCHAR(100) NOT NULL,    -- Vietful internal product ID
    sku          VARCHAR(100) NOT NULL,    -- product SKU (sku field from Vietful)
    partner_sku  VARCHAR(100),             -- partnerSKU from Vietful

    -- Basic product info
    name         VARCHAR(500),            -- productName
    unit_code    VARCHAR(50),             -- unitCode
    unit_name    VARCHAR(100),            -- unitName

    -- Category
    category_code VARCHAR(100),           -- categories[0].categoryCode
    category_name VARCHAR(255),           -- categories[0].categoryName

    -- Inventory tracking
    quantity     INTEGER      DEFAULT 0,
    price        NUMERIC(18, 2),          -- sellingPrice from productUnits

    -- Product attributes
    color        VARCHAR(100),            -- color
    size         VARCHAR(50),             -- size
    description  TEXT,                   -- description
    avatar_url   VARCHAR(1000),          -- avatarURL
    serial_type  SMALLINT,               -- serialType (1=Serial, etc.)
    asset_type   VARCHAR(50),            -- assetType (Single, Bundle)
    is_physical  BOOLEAN      DEFAULT false,

    -- CDMS tracking fields (exactly-once + old-data detection)
    version      BIGINT       DEFAULT 0,  -- monotonically increasing; used to detect old data
    content_hash VARCHAR(64),             -- SHA-256 of key fields; skip if data unchanged
    source       VARCHAR(50),            -- ingestion source: WEBHOOK | SCHEDULER | EXCEL

    -- Timestamps
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),

    -- Uniqueness constraint: one product per (external_id, sku)
    CONSTRAINT uq_products_external_id_sku UNIQUE (external_id, sku)
);

-- Index for fast lookups
CREATE INDEX IF NOT EXISTS idx_products_external_id ON products (external_id);
CREATE INDEX IF NOT EXISTS idx_products_sku ON products (sku);
CREATE INDEX IF NOT EXISTS idx_products_source ON products (source);
CREATE INDEX IF NOT EXISTS idx_products_updated_at ON products (updated_at);
