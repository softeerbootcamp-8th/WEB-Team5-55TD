-- 회원별 경매의 마지막 입찰을 찾는 상관 서브쿼리를 커버한다.
-- member_id 단일 인덱스의 역할도 왼쪽 접두사로 대신하므로 새 인덱스를 먼저 만든 뒤 제거한다.
SET @idx_bid_member_auction_id_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bid'
      AND index_name = 'idx_bid_member_auction_id'
);

SET @sql := IF(@idx_bid_member_auction_id_exists = 0,
    'ALTER TABLE bid ADD INDEX idx_bid_member_auction_id (member_id, auction_id, bid_id)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_bid_member_id_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bid'
      AND index_name = 'idx_bid_member_id'
);

SET @sql := IF(@idx_bid_member_id_exists > 0,
    'ALTER TABLE bid DROP INDEX idx_bid_member_id',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
