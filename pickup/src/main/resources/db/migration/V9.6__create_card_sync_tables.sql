ALTER TABLE card
    ADD COLUMN tcgdex_id VARCHAR(100) NULL,
    ADD COLUMN tcgdex_set_id VARCHAR(100) NULL,
    ADD UNIQUE INDEX uk_card_tcgdex_id (tcgdex_id),
    ADD INDEX idx_card_tcgdex_set_id (tcgdex_set_id);

CREATE TABLE card_set_sync (
    tcgdex_set_id VARCHAR(100) NOT NULL,
    set_name VARCHAR(255) NOT NULL,
    expected_card_count INT NOT NULL,
    release_date DATE NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_synced_at DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    PRIMARY KEY (tcgdex_set_id),
    INDEX idx_card_set_sync_status (status)
);
