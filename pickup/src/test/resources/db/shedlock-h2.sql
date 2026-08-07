-- 테스트용 shedlock 테이블. 운영은 Flyway(V8.2)가 만들지만, 테스트는 Flyway 를 끄고
-- ddl-auto 로 스키마를 만들기 때문에 JPA 엔티티가 아닌 이 테이블은 생성되지 않는다.
-- 컨텍스트 refresh 중에 실행되어 스케줄러 첫 실행보다 먼저 준비된다.
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
