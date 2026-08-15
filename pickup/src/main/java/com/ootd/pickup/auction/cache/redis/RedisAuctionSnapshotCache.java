package com.ootd.pickup.auction.cache.redis;

import com.ootd.pickup.auction.cache.AuctionSnapshot;
import com.ootd.pickup.auction.cache.AuctionSnapshotCache;
import com.ootd.pickup.auction.cache.AuctionSnapshotCacheProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link AuctionSnapshotCache}의 Redis 구현체.
 *
 * <p>경매마다 문자열 키 하나에 {@link AuctionSnapshot}을 JSON으로 담는다. 조회·저장 모두 실패를 삼키고 로그만 남긴다 — 이 캐시는 입찰 요청 생성
 * API의 사전 필터용일 뿐이라, Redis 장애가 요청 흐름을 막아서는 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAuctionSnapshotCache implements AuctionSnapshotCache {

  private static final String KEY_FORMAT = "pickup:auction-snapshot:%d";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final AuctionSnapshotCacheProperties properties;

  @Override
  public Optional<AuctionSnapshot> find(Long auctionId) {
    try {
      String value = redisTemplate.opsForValue().get(keyOf(auctionId));
      if (value == null) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(value, AuctionSnapshot.class));
    } catch (RuntimeException exception) {
      log.warn("경매 스냅샷 캐시 조회에 실패했습니다 - auctionId={}", auctionId, exception);
      return Optional.empty();
    }
  }

  @Override
  public void put(AuctionSnapshot snapshot) {
    try {
      String value = objectMapper.writeValueAsString(snapshot);
      redisTemplate.opsForValue().set(keyOf(snapshot.auctionId()), value, properties.ttl());
    } catch (RuntimeException exception) {
      log.warn("경매 스냅샷 캐시 저장에 실패했습니다 - auctionId={}", snapshot.auctionId(), exception);
    }
  }

  private String keyOf(Long auctionId) {
    return KEY_FORMAT.formatted(auctionId);
  }
}
