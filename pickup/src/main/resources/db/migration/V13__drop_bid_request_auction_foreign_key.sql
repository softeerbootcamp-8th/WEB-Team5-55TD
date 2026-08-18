-- 비동기 입찰 접수는 경매 행의 비관적 락과 독립적으로 커밋돼야 한다.
--
-- bid_request INSERT 시 이 외래키를 검증하려고 InnoDB가 부모 auction 행에 공유 락을
-- 요청한다. 같은 경매의 입찰 처리기가 배타 락을 잡고 있으면 접수 트랜잭션도 함께
-- 대기하고, 대기하는 동안 JDBC 커넥션을 점유해 비동기화의 풀 격리 효과가 사라진다.
-- 경매 존재 여부는 BidRequestService가 INSERT 전에 검증하며, auction_id 조회 인덱스는
-- 외래키와 별개로 유지한다.

SET @bid_request_auction_fk_exists := (
    SELECT COUNT(*)
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'bid_request'
      AND constraint_name = 'fk_bid_request_auction'
);

SET @sql := IF(
    @bid_request_auction_fk_exists > 0,
    'ALTER TABLE bid_request DROP FOREIGN KEY fk_bid_request_auction',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
