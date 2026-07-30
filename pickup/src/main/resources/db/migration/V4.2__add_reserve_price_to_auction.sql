ALTER TABLE auction
    ADD COLUMN IF NOT EXISTS reserve_price BIGINT NOT NULL AFTER starting_price;

ALTER TABLE auction
    MODIFY COLUMN ended_at DATETIME(6) NULL;
