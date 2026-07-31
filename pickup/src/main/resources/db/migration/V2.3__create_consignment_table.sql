CREATE TABLE IF NOT EXISTS consignment (
    consignment_id   BIGINT       AUTO_INCREMENT,
    card_id          BIGINT       NOT NULL,
    seller_member_id BIGINT       NOT NULL,
    major_defect     VARCHAR(255),
    status           VARCHAR(255) NOT NULL,
    PRIMARY KEY (consignment_id),
    CONSTRAINT fk_consignment_card FOREIGN KEY (card_id) REFERENCES card (card_id)
);

SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'consignment'
      AND index_name = 'idx_consignment_status'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE consignment ADD INDEX idx_consignment_status (status)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'card'
      AND column_name = 'is_deleted'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE card ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

