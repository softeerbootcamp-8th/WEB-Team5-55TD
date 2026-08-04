package com.ootd.pickup.bid.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BidPriceCacheRedisRepository implements BidPriceCacheRepository {
  private static final String KEY_PREFIX = "auction:current-price:";
  private static final Duration TTL = Duration.ofHours(24);

  private final StringRedisTemplate redisTemplate;

  @Override
  public Optional<Long> findCurrentPrice(Long auctionId) {
    try {
      String value = redisTemplate.opsForValue().get(key(auctionId));
      return value == null ? Optional.empty() : Optional.of(Long.valueOf(value));
    } catch (DataAccessException exception) {
      log.error("현재가 캐시 조회 실패 - operation=find, auctionId={}", auctionId, exception);
      return Optional.empty();
    }
  }

  @Override
  public void saveCurrentPrice(Long auctionId, Long currentPrice) {
    try {
      redisTemplate.opsForValue().set(key(auctionId), currentPrice.toString(), TTL);
    } catch (DataAccessException exception) {
      log.error(
          "현재가 캐시 갱신 실패 - operation=save, auctionId={}, currentPrice={}",
          auctionId,
          currentPrice,
          exception);
    }
  }

  private String key(Long auctionId) {
    return KEY_PREFIX + auctionId;
  }
}
