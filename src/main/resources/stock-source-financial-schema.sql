CREATE TABLE IF NOT EXISTS stock_source_financial_document (
    pack_version VARCHAR(80) NOT NULL,
    identity_sha256 CHAR(64) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    publication_date DATE NOT NULL,
    admitted_at_utc DATETIME(6) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    payload_json LONGTEXT NOT NULL CHECK (JSON_VALID(payload_json)),
    PRIMARY KEY (pack_version,identity_sha256),
    KEY idx_source_financial_symbol (symbol,pack_version,publication_date)
);
