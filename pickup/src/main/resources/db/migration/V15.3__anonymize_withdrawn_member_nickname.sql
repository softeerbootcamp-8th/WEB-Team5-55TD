UPDATE member
SET nickname = CONCAT('탈퇴회원_', member_id)
WHERE status = 'WITHDRAWN';
