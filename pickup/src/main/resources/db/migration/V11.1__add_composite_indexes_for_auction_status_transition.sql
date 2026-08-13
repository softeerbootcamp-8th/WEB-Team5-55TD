-- 경매 스케줄러가 시작 대상을 조회할 때 상태와 시작 시각을 함께 건다.
SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'auction'
      AND index_name = 'idx_auction_status_started_at'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE auction ADD INDEX idx_auction_status_started_at (auction_status, started_at)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 종료 대상 조회도 같은 형태다.
SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'auction'
      AND index_name = 'idx_auction_status_ended_at'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE auction ADD INDEX idx_auction_status_ended_at (auction_status, ended_at)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
