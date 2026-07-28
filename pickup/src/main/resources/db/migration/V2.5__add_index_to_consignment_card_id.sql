SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'consignment'
      AND index_name = 'idx_consignment_card_id'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE consignment ADD INDEX idx_consignment_card_id (card_id)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;