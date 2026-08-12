-- bid_request_id를 통해 같은 입찰 요청이 두 번 입찰로 이어지는 것을 막는다.
-- SQS는 at-least-once 전달이라 BidRequestCreatedMessageQueueEvent가 재전달될 수 있고,
-- 이 유니크 제약이 두 번째 삽입을 막아 재처리를 안전하게 감지하게 한다.
SET @bid_request_id_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bid'
      AND column_name = 'bid_request_id'
);

SET @sql := IF(@bid_request_id_exists = 0,
    'ALTER TABLE bid ADD COLUMN bid_request_id BIGINT',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_bid_bid_request_exists := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'bid'
      AND constraint_name = 'fk_bid_bid_request'
);

SET @sql := IF(@fk_bid_bid_request_exists = 0,
    'ALTER TABLE bid ADD CONSTRAINT fk_bid_bid_request FOREIGN KEY (bid_request_id) REFERENCES bid_request (bid_request_id)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @uk_bid_bid_request_id_exists := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'bid'
      AND constraint_name = 'uk_bid_bid_request_id'
);

SET @sql := IF(@uk_bid_bid_request_id_exists = 0,
    'ALTER TABLE bid ADD CONSTRAINT uk_bid_bid_request_id UNIQUE (bid_request_id)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
