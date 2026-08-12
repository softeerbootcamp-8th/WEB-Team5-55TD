-- 카드 실물 상태(스크래치·모서리 손상 등)를 담는다. 감정 등급(certificate.grade)과는 별개이며,
-- 그전까지 경매 상세의 cardState 는 감정 등급 표시명을 복제해 내려주고 있었다.
SET @card_state_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'consignment'
      AND column_name = 'card_state'
);

SET @sql := IF(@card_state_exists = 0,
    'ALTER TABLE consignment ADD COLUMN card_state VARCHAR(255)',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
