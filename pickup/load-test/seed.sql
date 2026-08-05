-- OOTD-292 부하테스트용 시드 데이터
-- 사전 조건: 셀러 계정은 API로 미리 생성해 두고, 그 memberId를 아래 @seller_member_id에 채워 넣는다.
--   curl -X POST $BASE_URL/members -H 'Content-Type: application/json' \
--     -d '{"loginId":"loadtest_seller","nickname":"부하테스트셀러","password":"password123"}'
--
-- 방식(A/B/C/D)마다 카드/상품/경매를 독립적으로 하나씩 만든다. 같은 경매를 공유하면
-- 한 방식의 테스트가 남긴 currentPrice가 다음 방식의 초기 조건을 오염시키기 때문이다.
-- A = 분산 락(/bids/distributed-lock), B = 조건부 UPDATE(/bids/conditional-update),
-- C = 조건부 UPDATE+캐시(/bids, 기본 엔드포인트), D = 짧은 트랜잭션(/bids/short-transaction)

SET @seller_member_id = 0; -- TODO: 실제 셀러 memberId로 교체

-- ===== A: 분산 락 =====
INSERT INTO card (card_name, card_number, set_name, language, rarity, image_url)
VALUES ('부하테스트카드-A', 'LOADTEST-A', '부하테스트세트', 'KOREAN', 'MINT', 'https://example.com/loadtest.png');
SET @card_id_a = LAST_INSERT_ID();

INSERT INTO consignment (card_id, seller_member_id, status)
VALUES (@card_id_a, @seller_member_id, 'AUCTION_SCHEDULED');
SET @consignment_id_a = LAST_INSERT_ID();

INSERT INTO auction
    (consignment_id, started_at, ended_at, auction_status, starting_price, reserve_price, bid_increment, current_price, created_at)
VALUES
    (@consignment_id_a, NOW(), DATE_ADD(NOW(), INTERVAL 6 HOUR), 'ONGOING', 10000, 15000, 100, 10000, NOW());
SET @auction_id_a = LAST_INSERT_ID();

-- ===== B: 조건부 UPDATE (캐시 없음) =====
INSERT INTO card (card_name, card_number, set_name, language, rarity, image_url)
VALUES ('부하테스트카드-B', 'LOADTEST-B', '부하테스트세트', 'KOREAN', 'MINT', 'https://example.com/loadtest.png');
SET @card_id_b = LAST_INSERT_ID();

INSERT INTO consignment (card_id, seller_member_id, status)
VALUES (@card_id_b, @seller_member_id, 'AUCTION_SCHEDULED');
SET @consignment_id_b = LAST_INSERT_ID();

INSERT INTO auction
    (consignment_id, started_at, ended_at, auction_status, starting_price, reserve_price, bid_increment, current_price, created_at)
VALUES
    (@consignment_id_b, NOW(), DATE_ADD(NOW(), INTERVAL 6 HOUR), 'ONGOING', 10000, 15000, 100, 10000, NOW());
SET @auction_id_b = LAST_INSERT_ID();

-- ===== C: 조건부 UPDATE + Redis 캐시 (기본 /bids 엔드포인트) =====
INSERT INTO card (card_name, card_number, set_name, language, rarity, image_url)
VALUES ('부하테스트카드-C', 'LOADTEST-C', '부하테스트세트', 'KOREAN', 'MINT', 'https://example.com/loadtest.png');
SET @card_id_c = LAST_INSERT_ID();

INSERT INTO consignment (card_id, seller_member_id, status)
VALUES (@card_id_c, @seller_member_id, 'AUCTION_SCHEDULED');
SET @consignment_id_c = LAST_INSERT_ID();

INSERT INTO auction
    (consignment_id, started_at, ended_at, auction_status, starting_price, reserve_price, bid_increment, current_price, created_at)
VALUES
    (@consignment_id_c, NOW(), DATE_ADD(NOW(), INTERVAL 6 HOUR), 'ONGOING', 10000, 15000, 100, 10000, NOW());
SET @auction_id_c = LAST_INSERT_ID();

-- ===== D: 짧은 트랜잭션 + 비동기 Bid 기록 =====
INSERT INTO card (card_name, card_number, set_name, language, rarity, image_url)
VALUES ('부하테스트카드-D', 'LOADTEST-D', '부하테스트세트', 'KOREAN', 'MINT', 'https://example.com/loadtest.png');
SET @card_id_d = LAST_INSERT_ID();

INSERT INTO consignment (card_id, seller_member_id, status)
VALUES (@card_id_d, @seller_member_id, 'AUCTION_SCHEDULED');
SET @consignment_id_d = LAST_INSERT_ID();

INSERT INTO auction
    (consignment_id, started_at, ended_at, auction_status, starting_price, reserve_price, bid_increment, current_price, created_at)
VALUES
    (@consignment_id_d, NOW(), DATE_ADD(NOW(), INTERVAL 6 HOUR), 'ONGOING', 10000, 15000, 100, 10000, NOW());
SET @auction_id_d = LAST_INSERT_ID();

SELECT
    @auction_id_a AS auction_id_distributed_lock,
    @auction_id_b AS auction_id_conditional_update,
    @auction_id_c AS auction_id_cached,
    @auction_id_d AS auction_id_short_transaction;
