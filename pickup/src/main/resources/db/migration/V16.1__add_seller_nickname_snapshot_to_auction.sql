-- 탈퇴 후에도 nickname 컬럼을 재사용할 수 있도록 Member.withdraw()가 nickname을 null로 비운다.
-- 과거 경매의 판매자 닉네임을 그대로 보존하기 위해 등록 시점 닉네임을 스냅샷으로 저장한다.
-- 백필 값은 "현재" 닉네임 기준 근사치다 — 이미 탈퇴해 nickname이 null인 회원은 '탈퇴한 회원'으로 채우며,
-- 어차피 응답 조립 시 member.isWithdrawn() 여부로 다시 덮어써 표시되므로 문제 없다.
ALTER TABLE auction ADD COLUMN seller_nickname_snapshot VARCHAR(255);

UPDATE auction
SET seller_nickname_snapshot = (
    SELECT COALESCE(m.nickname, '탈퇴한 회원')
    FROM consignment c
    JOIN member m ON c.seller_member_id = m.member_id
    WHERE c.consignment_id = auction.consignment_id
);

ALTER TABLE auction MODIFY seller_nickname_snapshot VARCHAR(255) NOT NULL;
