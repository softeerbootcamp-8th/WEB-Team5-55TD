-- POPULAR 정렬에서 경매마다 watch를 다시 집계하는 상관 서브쿼리를 제거한다.
SET @auction_watch_count_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'auction'
      AND column_name = 'watch_count'
);

SET @sql := IF(@auction_watch_count_exists = 0,
    'ALTER TABLE auction ADD COLUMN watch_count BIGINT NOT NULL DEFAULT 0 AFTER winning_price',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE auction a
LEFT JOIN (
    SELECT auction_id, COUNT(*) AS watch_count
    FROM watch
    GROUP BY auction_id
) w ON w.auction_id = a.auction_id
SET a.watch_count = COALESCE(w.watch_count, 0);

SET @idx_auction_status_watch_count_id_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'auction'
      AND index_name = 'idx_auction_status_watch_count_id'
);

SET @sql := IF(@idx_auction_status_watch_count_id_exists = 0,
    'ALTER TABLE auction ADD INDEX idx_auction_status_watch_count_id (auction_status, watch_count DESC, auction_id DESC)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
