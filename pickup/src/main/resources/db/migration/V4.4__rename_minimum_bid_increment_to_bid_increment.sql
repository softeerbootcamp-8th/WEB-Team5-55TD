ALTER TABLE auction
    CHANGE COLUMN minimum_bid_increment bid_increment BIGINT NOT NULL;
