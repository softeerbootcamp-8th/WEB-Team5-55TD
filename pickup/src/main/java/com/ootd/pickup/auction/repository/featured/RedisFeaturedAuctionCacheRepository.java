package com.ootd.pickup.auction.repository.featured;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisFeaturedAuctionCacheRepository implements FeaturedAuctionCacheRepository {

  private static final String FEATURED_AUCTION_CACHE_KEY = "auction:featured:id";

  private final StringRedisTemplate redisTemplate;

  @Override
  public Optional<Long> getFeaturedAuctionId() {
    try {
      String cachedId = redisTemplate.opsForValue().get(FEATURED_AUCTION_CACHE_KEY);
      if (cachedId != null) {
        return Optional.of(Long.parseLong(cachedId));
      }
    } catch (Exception ignored) {
    }
    return Optional.empty();
  }

  @Override
  public void setFeaturedAuctionId(Long auctionId, Duration ttl) {
    try {
      redisTemplate.opsForValue().set(FEATURED_AUCTION_CACHE_KEY, String.valueOf(auctionId), ttl);
    } catch (Exception ignored) {
    }
  }

  @Override
  public void evictFeaturedAuctionId() {
    try {
      redisTemplate.delete(FEATURED_AUCTION_CACHE_KEY);
    } catch (Exception ignored) {
    }
  }
}
