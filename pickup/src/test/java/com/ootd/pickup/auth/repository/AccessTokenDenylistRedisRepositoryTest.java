package com.ootd.pickup.auth.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AccessTokenDenylistRedisRepositoryTest {
  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private AccessTokenDenylistRedisRepository accessTokenDenylistRepository;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    accessTokenDenylistRepository = new AccessTokenDenylistRedisRepository(redisTemplate);
  }

  @Test
  void 회원을_남은_만료시간만큼_거부_목록에_올린다() {
    // given
    Duration ttl = Duration.ofMinutes(15);

    // when
    accessTokenDenylistRepository.denylistMember(1L, ttl);

    // then
    verify(valueOperations).set("auth:access:denylist:1", "1", ttl);
  }

  @Test
  void 남은_만료시간이_없으면_거부_목록에_올리지_않는다() {
    // when
    accessTokenDenylistRepository.denylistMember(1L, Duration.ZERO);

    // then
    verifyNoInteractions(valueOperations);
  }

  @Test
  void 거부_목록에_있는_회원은_거부된_것으로_조회된다() {
    // given
    when(redisTemplate.hasKey("auth:access:denylist:1")).thenReturn(true);

    // when & then
    assertThat(accessTokenDenylistRepository.isDenylisted(1L)).isTrue();
  }

  @Test
  void 거부_목록에_없는_회원은_거부되지_않은_것으로_조회된다() {
    // given
    when(redisTemplate.hasKey("auth:access:denylist:1")).thenReturn(false);

    // when & then
    assertThat(accessTokenDenylistRepository.isDenylisted(1L)).isFalse();
  }

  @Test
  void 조회중_레디스_장애가_발생하면_거부되지_않은_것으로_취급한다() {
    // given
    when(redisTemplate.hasKey("auth:access:denylist:1"))
        .thenThrow(new RedisConnectionFailureException("Redis connection failed"));

    // when & then
    assertThat(accessTokenDenylistRepository.isDenylisted(1L)).isFalse();
  }
}
