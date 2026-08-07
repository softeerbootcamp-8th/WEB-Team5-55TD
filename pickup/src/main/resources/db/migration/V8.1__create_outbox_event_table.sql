-- 메시지 큐 이벤트(유실 불가)를 도메인 트랜잭션과 같은 커밋에 적재해두는 Outbox.
-- OutboxEventScheduler 가 published=false 인 행을 읽어 EventProducer 로 발행한다.
CREATE TABLE IF NOT EXISTS outbox_event (
    id             VARCHAR(36)  NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    payload        JSON         NOT NULL,
    published      BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 같은 초에 쌓인 이벤트의 발행 순서를 가리려면 초 단위로는 부족하다.
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_event_published_created_at (published, created_at)
);
