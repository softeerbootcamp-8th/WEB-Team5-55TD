CREATE TABLE IF NOT EXISTS auction (
    auction_id             BIGINT       AUTO_INCREMENT,
    consignment_id             BIGINT       NOT NULL,
    winning_bid_id         BIGINT,
    started_at             DATETIME(6)  NOT NULL,
    ended_at               DATETIME(6)  NOT NULL,
    auction_status         VARCHAR(255) NOT NULL,
    starting_price         BIGINT       NOT NULL,
    minimum_bid_increment  BIGINT       NOT NULL,
    winning_price          BIGINT,
    created_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (auction_id),
    CONSTRAINT fk_auction_consignment FOREIGN KEY (consignment_id) REFERENCES consignment (consignment_id),
    INDEX idx_auction_consignment_id (consignment_id),
    INDEX idx_auction_status (auction_status)
);
