SET @exists := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'consignment'
      AND constraint_name = 'fk_consignment_seller_member'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE consignment ADD CONSTRAINT fk_consignment_seller_member FOREIGN KEY (seller_member_id) REFERENCES member (member_id)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
