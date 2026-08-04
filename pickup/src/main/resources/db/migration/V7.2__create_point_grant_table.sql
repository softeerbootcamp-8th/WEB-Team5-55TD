CREATE TABLE IF NOT EXISTS point_grant (
    point_grant_id BIGINT       AUTO_INCREMENT,
    member_id      BIGINT       NOT NULL,
    admin_id       BIGINT       NOT NULL,
    amount         BIGINT       NOT NULL,
    reason         VARCHAR(255),
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (point_grant_id),
    INDEX idx_point_grant_member_id (member_id),
    INDEX idx_point_grant_admin_id (admin_id)
);
