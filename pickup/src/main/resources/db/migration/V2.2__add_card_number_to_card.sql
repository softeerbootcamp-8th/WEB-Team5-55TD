SET @exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'card'
      AND column_name = 'card_number'
);

SET @sql := IF(@exists = 0,
    "ALTER TABLE card ADD COLUMN card_number VARCHAR(255) NOT NULL DEFAULT ''",
    "SELECT 1");

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE card
    ALTER COLUMN card_number DROP DEFAULT;