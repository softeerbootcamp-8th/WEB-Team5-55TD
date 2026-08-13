package com.ootd.pickup.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LoginAttemptLimiterTest {
  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private LoginAttemptLimiter loginAttemptLimiter;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    loginAttemptLimiter = new LoginAttemptLimiter(redisTemplate);
  }

  @Test
  void 첫_시도면_윈도우_만료시간을_설정한다() {
    // given
    when(valueOperations.increment("auth:login-attempts:pickup-user")).thenReturn(1L);

    // when
    loginAttemptLimiter.checkAllowed("pickup-user");

    // then
    verify(redisTemplate).expire("auth:login-attempts:pickup-user", Duration.ofMinutes(5));
  }

  @Test
  void 첫_시도가_아니면_만료시간을_다시_설정하지_않는다() {
    // given
    when(valueOperations.increment("auth:login-attempts:pickup-user")).thenReturn(2L);

    // when
    loginAttemptLimiter.checkAllowed("pickup-user");

    // then
    verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
  }

  @Test
  void 한도_이내면_통과한다() {
    // given
    when(valueOperations.increment("auth:login-attempts:pickup-user")).thenReturn(5L);

    // when & then
    assertThatCode(() -> loginAttemptLimiter.checkAllowed("pickup-user"))
        .doesNotThrowAnyException();
  }

  @Test
  void 한도를_초과하면_거절한다() {
    // given
    when(valueOperations.increment("auth:login-attempts:pickup-user")).thenReturn(6L);

    // when & then
    assertThatThrownBy(() -> loginAttemptLimiter.checkAllowed("pickup-user"))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.TOO_MANY_LOGIN_ATTEMPTS.getMessage());
  }

  @Test
  void 저장소_장애가_발생하면_제한을_생략하고_통과시킨다() {
    // given
    when(valueOperations.increment("auth:login-attempts:pickup-user"))
        .thenThrow(new RedisConnectionFailureException("Redis connection failed"));

    // when & then
    assertThatCode(() -> loginAttemptLimiter.checkAllowed("pickup-user"))
        .doesNotThrowAnyException();
  }

  @Test
  void 초기화하면_시도_횟수_키를_지운다() {
    // when
    loginAttemptLimiter.reset("pickup-user");

    // then
    verify(redisTemplate).delete("auth:login-attempts:pickup-user");
  }
}
