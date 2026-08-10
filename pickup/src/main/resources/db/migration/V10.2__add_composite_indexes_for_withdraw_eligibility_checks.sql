-- 회원 탈퇴 가능 여부 확인(MemberService.hasActiveConsignment/hasActiveBid)이
-- member_id(또는 seller_member_id)와 상태를 함께 필터링하는데, 기존에는 각각
-- 단일 컬럼 인덱스만 있어 상태 필터링을 인덱스만으로 끝내지 못했다. 복합 인덱스를
-- 추가해 인덱스 레인지 스캔으로 바로 끝나게 한다.
SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bid'
      AND index_name = 'idx_bid_member_id_status'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE bid ADD INDEX idx_bid_member_id_status (member_id, bid_status)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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
