ALTER TABLE member_point
    ADD COLUMN reserved_balance BIGINT NOT NULL DEFAULT 0 AFTER balance;

ALTER TABLE member_point
    ADD CONSTRAINT chk_member_point_balance_non_negative CHECK (balance >= 0),
    ADD CONSTRAINT chk_member_point_reserved_balance_non_negative CHECK (reserved_balance >= 0),
    ADD CONSTRAINT chk_member_point_reserved_not_over_balance CHECK (reserved_balance <= balance);

ALTER TABLE auction
    ADD COLUMN legacy_unreserved_bid BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE auction
SET legacy_unreserved_bid = TRUE
WHERE auction_status = 'ONGOING'
  AND winning_bid_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS point_reservation (
    point_reservation_id BIGINT       AUTO_INCREMENT,
    auction_id           BIGINT       NOT NULL,
    bid_id               BIGINT       NOT NULL,
    member_id            BIGINT       NOT NULL,
    amount               BIGINT       NOT NULL,
    reservation_status   VARCHAR(255) NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (point_reservation_id),
    CONSTRAINT fk_point_reservation_auction FOREIGN KEY (auction_id) REFERENCES auction (auction_id),
    CONSTRAINT fk_point_reservation_bid FOREIGN KEY (bid_id) REFERENCES bid (bid_id),
    CONSTRAINT fk_point_reservation_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT uk_point_reservation_auction UNIQUE (auction_id),
    CONSTRAINT uk_point_reservation_bid UNIQUE (bid_id),
    CONSTRAINT chk_point_reservation_amount_positive CHECK (amount > 0),
    INDEX idx_point_reservation_member_status (member_id, reservation_status)
);

CREATE TABLE IF NOT EXISTS point_transaction (
    point_transaction_id BIGINT       AUTO_INCREMENT,
    member_id            BIGINT       NOT NULL,
    transaction_type     VARCHAR(255) NOT NULL,
    amount               BIGINT       NOT NULL,
    balance_after        BIGINT       NOT NULL,
    auction_id           BIGINT,
    idempotency_key      VARCHAR(128) NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (point_transaction_id),
    CONSTRAINT fk_point_transaction_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_point_transaction_auction FOREIGN KEY (auction_id) REFERENCES auction (auction_id),
    CONSTRAINT uk_point_transaction_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_point_transaction_amount_non_zero CHECK (amount <> 0),
    CONSTRAINT chk_point_transaction_balance_after_non_negative CHECK (balance_after >= 0),
    INDEX idx_point_transaction_member_id_id (member_id, point_transaction_id)
);

INSERT INTO point_transaction (
    member_id,
    transaction_type,
    amount,
    balance_after,
    auction_id,
    idempotency_key,
    created_at
)
SELECT
    member_id,
    'OPENING_BALANCE',
    balance,
    balance,
    NULL,
    CONCAT('OPENING_BALANCE:', member_id),
    CURRENT_TIMESTAMP(6)
FROM member_point
WHERE balance <> 0;
