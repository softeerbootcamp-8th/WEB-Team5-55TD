CREATE TABLE IF NOT EXISTS admin (
    admin_id   BIGINT       AUTO_INCREMENT,
    login_id   VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (admin_id),
    CONSTRAINT uk_admin_login_id UNIQUE (login_id)
);

-- 개발용 초기 관리자 계정. loginId=admin / password=admin1234
-- 배포 전 반드시 비밀번호를 교체해야 한다.
INSERT INTO admin (login_id, password, name, created_at)
SELECT 'admin', '$2y$12$NyU7KZchH6.FW6DYqAvEI.fRO3n4plf4dheYsjmeC2/f2HiWYWLsW', '기본 관리자', NOW()
WHERE NOT EXISTS (SELECT 1 FROM admin WHERE login_id = 'admin');
