SET @has_image_url = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'consignment_image'
      AND column_name = 'image_url'
);

SET @sql = IF(
    @has_image_url > 0,
    'DELETE FROM consignment_image',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    @has_image_url > 0,
    'ALTER TABLE consignment_image CHANGE COLUMN image_url object_key VARCHAR(512) NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
