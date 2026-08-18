-- ShedLock 규격 테이블. 컬럼명·타입은 JdbcTemplateLockProvider 가 기대하는 형태라 바꾸면 동작하지 않는다.
-- lock_until 이 MySQL 의 암묵적 DEFAULT CURRENT_TIMESTAMP / ON UPDATE 대상이 되면 락 만료 시각이 갱신마다
-- 덮여 중복 실행이 발생한다. MySQL 8 기본값(explicit_defaults_for_timestamp=ON)에서는 붙지 않는다.
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
