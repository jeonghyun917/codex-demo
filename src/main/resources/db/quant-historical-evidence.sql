-- Additive, evidence-only staging. These tables do NOT certify PIT membership or adjusted prices.
CREATE TABLE IF NOT EXISTS stock_historical_evidence_bundle (
    bundle_sha256 CHAR(64) NOT NULL PRIMARY KEY,
    local_path TEXT NOT NULL,
    coverage_claim VARCHAR(80) NOT NULL,
    manifest_json LONGTEXT NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stock_historical_evidence_source (
    source_key CHAR(64) NOT NULL PRIMARY KEY,
    source_url TEXT NOT NULL,
    publisher VARCHAR(200) NOT NULL,
    publication_date DATE NULL,
    retrieved_at_utc DATETIME(6) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    local_path TEXT NOT NULL,
    coverage_claim VARCHAR(80) NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stock_historical_evidence_event (
    event_key CHAR(64) NOT NULL PRIMARY KEY,
    source_key CHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    index_code VARCHAR(16) NULL,
    action_kind VARCHAR(16) NOT NULL,
    effective_date DATE NOT NULL,
    date_basis VARCHAR(32) NULL,
    market_timing VARCHAR(24) NULL,
    evidence_json LONGTEXT NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_historical_evidence_source FOREIGN KEY (source_key)
        REFERENCES stock_historical_evidence_source(source_key),
    INDEX idx_historical_evidence_symbol_date (symbol, effective_date),
    INDEX idx_historical_evidence_index_date (index_code, effective_date)
);
