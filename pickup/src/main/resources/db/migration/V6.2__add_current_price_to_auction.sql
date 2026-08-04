SET @current_price_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'auction'
      AND column_name = 'current_price'
);

SET @sql := IF(@current_price_exists = 0,
    'ALTER TABLE auction ADD COLUMN current_price BIGINT NULL AFTER starting_price',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE auction
SET current_price = COALESCE(
    (SELECT bid.bid_price
     FROM bid
     WHERE bid.auction_id = auction.auction_id
       AND bid.bid_status = 'HIGHEST'
     LIMIT 1),
    auction.starting_price)
WHERE current_price IS NULL;

ALTER TABLE auction
    MODIFY COLUMN current_price BIGINT NOT NULL;
