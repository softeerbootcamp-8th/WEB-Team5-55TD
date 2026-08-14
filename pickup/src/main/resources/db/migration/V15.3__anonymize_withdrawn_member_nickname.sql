UPDATE member
SET nickname = CONCAT('(탈퇴한 회원)#', member_id)
WHERE status = 'WITHDRAWN';
