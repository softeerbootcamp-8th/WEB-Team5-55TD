package com.ootd.pickup.bid.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class BidPriceCacheRedisRepositoryTest {
  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private BidPriceCacheRedisRepository bidPriceCacheRepository;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    bidPriceCacheRepository = new BidPriceCacheRedisRepository(redisTemplate);
  }

  @Test
  void 현재가를_저장하면_경매ID_기반_키와_TTL로_저장된다() {
    // when
    bidPriceCacheRepository.saveCurrentPrice(1L, 10_500L);

    // then
    verify(valueOperations).set("auction:current-price:1", "10500", Duration.ofHours(24));
  }

  @Test
  void 저장된_현재가를_조회하면_숫자로_반환한다() {
    // given
    when(valueOperations.get("auction:current-price:1")).thenReturn("10500");

    // when & then
    assertThat(bidPriceCacheRepository.findCurrentPrice(1L)).contains(10_500L);
  }

  @Test
  void 캐시가_없으면_빈_값을_반환한다() {
    // given
    when(valueOperations.get("auction:current-price:1")).thenReturn(null);

    // when & then
    assertThat(bidPriceCacheRepository.findCurrentPrice(1L)).isEmpty();
  }

  @Test
  void 레디스_장애로_조회에_실패해도_예외를_전파하지_않고_빈_값을_반환한다() {
    // given
    when(valueOperations.get("auction:current-price:1"))
        .thenThrow(new RedisConnectionFailureException("Redis connection failed"));

    // when & then
    assertThat(bidPriceCacheRepository.findCurrentPrice(1L)).isEmpty();
  }

  @Test
  void 레디스_장애로_저장에_실패해도_예외를_전파하지_않는다() {
    // given
    doThrow(new RedisConnectionFailureException("Redis connection failed"))
        .when(valueOperations)
        .set("auction:current-price:1", "10500", Duration.ofHours(24));

    // when & then
    assertThatCode(() -> bidPriceCacheRepository.saveCurrentPrice(1L, 10_500L))
        .doesNotThrowAnyException();
  }
}
