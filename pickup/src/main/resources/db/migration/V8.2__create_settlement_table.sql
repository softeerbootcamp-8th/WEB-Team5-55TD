CREATE TABLE IF NOT EXISTS settlement (
    settlement_id      BIGINT       AUTO_INCREMENT,
    auction_id         BIGINT       NOT NULL,
    member_id          BIGINT       NOT NULL,
    settlement_type    VARCHAR(255) NOT NULL,
    amount             BIGINT       NOT NULL,
    settlement_status  VARCHAR(255) NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (settlement_id),
    CONSTRAINT fk_settlement_auction FOREIGN KEY (auction_id) REFERENCES auction (auction_id),
    CONSTRAINT fk_settlement_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    UNIQUE KEY uk_settlement_auction_member_type (auction_id, member_id, settlement_type),
    INDEX idx_settlement_member_id (member_id)
);
