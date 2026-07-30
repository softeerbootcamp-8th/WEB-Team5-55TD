SET @exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'auction'
      AND column_name = 'reserve_price'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE auction ADD COLUMN reserve_price BIGINT NOT NULL DEFAULT 0 AFTER starting_price',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE auction
    ALTER COLUMN reserve_price DROP DEFAULT;

ALTER TABLE auction
    MODIFY COLUMN ended_at DATETIME(6) NULL;
