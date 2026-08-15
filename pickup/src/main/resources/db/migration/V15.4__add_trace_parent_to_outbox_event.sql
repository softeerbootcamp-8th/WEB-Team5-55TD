-- 적재 시점(HTTP 요청 스레드)의 W3C traceparent를 함께 저장한다.
-- 릴레이가 SQS로 보낼 때 이 값을 실어 보내면, 시간이 지나 다른 스레드에서 도는 소비자가
-- 원래 요청과 같은 트레이스로 스팬을 이어 붙일 수 있다. 적재 시점에 활성 스팬이 없으면 NULL이다.
ALTER TABLE outbox_event
    ADD COLUMN trace_parent VARCHAR(64) NULL AFTER created_at;
