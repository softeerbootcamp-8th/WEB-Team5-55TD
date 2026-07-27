CREATE TABLE IF NOT EXISTS member (
    member_id BIGINT AUTO_INCREMENT,
    login_id VARCHAR(255),
    password VARCHAR(255),
    nickname VARCHAR(255),
    joined_at DATETIME(6),
    updated_at DATETIME(6),
    profile_image_url VARCHAR(255),
    PRIMARY KEY (member_id),
    CONSTRAINT uk_member_login_id UNIQUE (login_id),
    CONSTRAINT uk_member_nickname UNIQUE (nickname)
);
