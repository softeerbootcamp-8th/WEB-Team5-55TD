CREATE TABLE IF NOT EXISTS member_point (
    point_id BIGINT AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (point_id),
    CONSTRAINT uk_member_point_member_id UNIQUE (member_id),
    CONSTRAINT fk_member_point_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

INSERT INTO member_point (member_id, balance)
SELECT member_id, 0
FROM member
WHERE NOT EXISTS (
    SELECT 1
    FROM member_point
    WHERE member_point.member_id = member.member_id
);
