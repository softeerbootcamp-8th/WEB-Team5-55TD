CREATE TABLE IF NOT EXISTS card (
                                    card_id    BIGINT       AUTO_INCREMENT,
                                    card_name  VARCHAR(255) NOT NULL,
    set_name   VARCHAR(255) NOT NULL,
    language   VARCHAR(255) NOT NULL,
    rarity     VARCHAR(255) NOT NULL,
    image_url  VARCHAR(255) NOT NULL,
    PRIMARY KEY (card_id)
    );

SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'card'
      AND index_name = 'idx_card_card_name'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE card ADD INDEX idx_card_card_name (card_name)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;