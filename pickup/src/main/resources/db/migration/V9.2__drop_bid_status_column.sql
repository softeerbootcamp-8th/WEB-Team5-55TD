-- bid_status는 이제 저장하지 않는다. "누가 최고/낙찰인지"는 auction.winning_bid_id와
-- auction.auction_status로 그때그때 계산한다(Bid.getBidStatus() 참고).
--
-- fk_bid_auction(bid.auction_id -> auction.auction_id)이 auction_id를 앞세운 인덱스를
-- 필요로 하는데, 지금은 idx_bid_auction_status_price(auction_id, bid_status, bid_price)가
-- 그 역할을 겸하고 있다. 대체 인덱스(idx_bid_auction_id)를 먼저 만들어야, FK가 인덱스 없이
-- 남는 순간 없이 옛 인덱스를 안전하게 지울 수 있다.
SET @idx_bid_auction_id_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bid'
      AND index_name = 'idx_bid_auction_id'
);

SET @sql := IF(@idx_bid_auction_id_exists = 0,
    'ALTER TABLE bid ADD INDEX idx_bid_auction_id (auction_id)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_bid_auction_status_price_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bid'
      AND index_name = 'idx_bid_auction_status_price'
);

SET @sql := IF(@idx_bid_auction_status_price_exists > 0,
    'ALTER TABLE bid DROP INDEX idx_bid_auction_status_price',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @bid_status_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bid'
      AND column_name = 'bid_status'
);

SET @sql := IF(@bid_status_exists > 0,
    'ALTER TABLE bid DROP COLUMN bid_status',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
