-- 라운드(반복 실행) 사이에 4개 시드 경매를 초기 상태로 되돌린다.
-- seed.sql 실행 후 얻은 4개 auction_id로 아래 IN(...) 목록을 채운다.
-- Redis 캐시(C, D 방식이 사용)는 이 스크립트로 지울 수 없으므로 redis-cli로 별도 삭제한다:
--   redis-cli DEL auction:current-price:<auction_id_c> auction:current-price:<auction_id_d>

SET @auction_id_a = 0; -- TODO
SET @auction_id_b = 0; -- TODO
SET @auction_id_c = 0; -- TODO
SET @auction_id_d = 0; -- TODO

DELETE FROM bid
WHERE auction_id IN (@auction_id_a, @auction_id_b, @auction_id_c, @auction_id_d);

UPDATE auction
SET current_price = starting_price,
    winning_bid_id = NULL,
    winning_price = NULL
WHERE auction_id IN (@auction_id_a, @auction_id_b, @auction_id_c, @auction_id_d);
