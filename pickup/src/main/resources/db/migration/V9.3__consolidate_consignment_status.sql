-- ConsignmentStatus를 REGISTERABLE/IN_AUCTION/SOLD 3단계로 합친다.
-- AUCTION_SCHEDULED/AUCTION_ONGOING -> IN_AUCTION (경매 신청부터 종료 전까지 구분하지 않음)
-- WON -> SOLD
-- PASSED -> REGISTERABLE (유찰도 재등록 가능한 상태라 신규 등록과 값을 합친다)
UPDATE consignment SET status = 'IN_AUCTION' WHERE status IN ('AUCTION_SCHEDULED', 'AUCTION_ONGOING');
UPDATE consignment SET status = 'SOLD' WHERE status = 'WON';
UPDATE consignment SET status = 'REGISTERABLE' WHERE status = 'PASSED';
