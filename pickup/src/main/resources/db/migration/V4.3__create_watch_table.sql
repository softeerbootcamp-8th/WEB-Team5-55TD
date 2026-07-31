CREATE TABLE IF NOT EXISTS watch (
    watch_id   BIGINT       AUTO_INCREMENT,
    auction_id BIGINT       NOT NULL,
    member_id  BIGINT       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (watch_id),
    CONSTRAINT uk_watch_auction_member UNIQUE (auction_id, member_id),
    INDEX idx_watch_auction_id (auction_id),
    INDEX idx_watch_member_id (member_id)
);
