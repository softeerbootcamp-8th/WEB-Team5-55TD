SET @card_state_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'consignment'
      AND column_name = 'card_state'
);

SET @sql := IF(@card_state_exists = 0,
    'ALTER TABLE consignment ADD COLUMN card_state VARCHAR(16)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
