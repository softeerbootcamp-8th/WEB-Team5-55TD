-- 회원 탈퇴 가능 여부 확인(BidService.hasActiveBid)은 "회원의 입찰가가 같은 경매의
-- 최고 입찰가와 같은가"를 직접 계산한다(bid_status는 별도 작업에서 정리될 예정이라
-- 의존하지 않음). 경매별 최고가를 구하는 서브쿼리가 auction_id로 좁혀 bid_price를
-- 읽으므로, (auction_id, bid_price) 복합 인덱스를 추가해 인덱스만으로 MAX를 구하게 한다.
SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bid'
      AND index_name = 'idx_bid_auction_id_bid_price'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE bid ADD INDEX idx_bid_auction_id_bid_price (auction_id, bid_price)',
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
