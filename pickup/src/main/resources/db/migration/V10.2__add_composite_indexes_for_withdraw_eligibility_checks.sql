-- 회원 탈퇴 가능 여부 확인 시 셀러의 IN_AUCTION 상품 존재 여부를 조회한다.
SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'consignment'
      AND index_name = 'idx_consignment_seller_member_id_status'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE consignment ADD INDEX idx_consignment_seller_member_id_status (seller_member_id, status)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
