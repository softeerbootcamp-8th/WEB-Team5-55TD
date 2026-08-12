-- 회원 탈퇴를 상태 전환으로 표현하기 위한 컬럼을 추가한다.
-- FK 제약(consignment/bid/member_point/settlement -> member)이 모두 ON DELETE 없이
-- 걸려 있어 물리 삭제가 불가능하므로, status=WITHDRAWN 전환으로 탈퇴를 표현한다.
SET @exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'member'
      AND column_name = 'status'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE member ADD COLUMN status VARCHAR(255) NOT NULL DEFAULT ''ACTIVE''',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'member'
      AND column_name = 'withdrawn_at'
);

SET @sql := IF(@exists = 0,
    'ALTER TABLE member ADD COLUMN withdrawn_at DATETIME(6) NULL',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
