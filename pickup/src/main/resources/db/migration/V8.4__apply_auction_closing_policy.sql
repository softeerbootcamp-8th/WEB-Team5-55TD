-- 기존 무기한 경매에도 7일 종료 정책을 적용한다.
UPDATE auction
SET ended_at = DATE_ADD(started_at, INTERVAL 7 DAY)
WHERE ended_at IS NULL;
