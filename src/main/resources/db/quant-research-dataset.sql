-- Additive registry. Research membership / benchmark rows live under isolated *_FREE_* index codes.
-- No operating membership, canonical prices, or strict promotion gates are modified.
CREATE TABLE IF NOT EXISTS stock_research_dataset (
    index_code VARCHAR(40) NOT NULL PRIMARY KEY,
    manifest_sha256 CHAR(64) NOT NULL,
    manifest_json LONGTEXT NOT NULL CHECK (JSON_VALID(manifest_json)),
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
