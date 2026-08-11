CREATE TABLE IF NOT EXISTS bid_request (
    bid_request_id  BIGINT       AUTO_INCREMENT,
    auction_id      BIGINT       NOT NULL,
    member_id       BIGINT       NOT NULL,
    bid_price       BIGINT       NOT NULL,
    status          VARCHAR(255) NOT NULL,
    failure_code    VARCHAR(50),
    failure_message VARCHAR(255),
    created_at      DATETIME(6)  NOT NULL,
    processed_at    DATETIME(6),
    PRIMARY KEY (bid_request_id),
    CONSTRAINT fk_bid_request_auction FOREIGN KEY (auction_id) REFERENCES auction (auction_id),
    CONSTRAINT fk_bid_request_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT chk_bid_request_bid_price_positive CHECK (bid_price > 0),
    INDEX idx_bid_request_auction_id (auction_id)
);
