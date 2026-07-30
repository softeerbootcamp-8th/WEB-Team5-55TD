SET @minimum_bid_increment_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'auction'
      AND column_name = 'minimum_bid_increment'
);

SET @sql := IF(@minimum_bid_increment_exists > 0,
    'ALTER TABLE auction CHANGE COLUMN minimum_bid_increment bid_increment BIGINT NOT NULL',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS bid (
    bid_id      BIGINT       AUTO_INCREMENT,
    auction_id  BIGINT       NOT NULL,
    member_id   BIGINT       NOT NULL,
    bid_price   BIGINT       NOT NULL,
    bid_status  VARCHAR(255) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (bid_id),
    CONSTRAINT fk_bid_auction FOREIGN KEY (auction_id) REFERENCES auction (auction_id),
    CONSTRAINT fk_bid_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    INDEX idx_bid_auction_status_price (auction_id, bid_status, bid_price),
    INDEX idx_bid_member_id (member_id)
);

SET @exists := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'auction'
      AND constraint_name = 'fk_auction_winning_bid'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE auction ADD CONSTRAINT fk_auction_winning_bid FOREIGN KEY (winning_bid_id) REFERENCES bid (bid_id)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
