package com.ootd.pickup.auth.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RefreshTokenRedisRepositoryTest {
  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private SetOperations<String, String> setOperations;
  private RefreshTokenRedisRepository refreshTokenRepository;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    setOperations = mock(SetOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    refreshTokenRepository = new RefreshTokenRedisRepository(redisTemplate);
  }

  @Test
  void 리프레시_토큰_해시와_회원_아이디를_저장한다() {
    // given
    Duration ttl = Duration.ofDays(14);

    // when
    refreshTokenRepository.save("token-hash", 1L, ttl);

    // then
    verify(valueOperations).set("auth:refresh:token-hash", "1", ttl);
    verify(setOperations).add("auth:refresh:member:1", "token-hash");
    verify(redisTemplate).expire("auth:refresh:member:1", ttl);
  }

  @Test
  void 리프레시_토큰을_조회하면서_삭제한다() {
    // given
    when(valueOperations.getAndDelete("auth:refresh:token-hash")).thenReturn("1");

    // when & then
    assertThat(refreshTokenRepository.consume("token-hash")).contains(1L);

    verify(valueOperations).getAndDelete("auth:refresh:token-hash");
    verify(setOperations).remove("auth:refresh:member:1", "token-hash");
  }

  @Test
  void 존재하지_않는_리프레시_토큰을_조회하면_빈_값을_반환한다() {
    // given
    when(valueOperations.getAndDelete("auth:refresh:token-hash")).thenReturn(null);

    // when & then
    assertThat(refreshTokenRepository.consume("token-hash")).isEmpty();
    verify(setOperations, never()).remove(anyString(), anyString());
  }

  @Test
  void 리프레시_토큰을_삭제한다() {
    // given
    when(valueOperations.get("auth:refresh:token-hash")).thenReturn("1");

    // when
    refreshTokenRepository.delete("token-hash");

    // then
    verify(redisTemplate).delete("auth:refresh:token-hash");
    verify(setOperations).remove("auth:refresh:member:1", "token-hash");
  }

  @Test
  void 회원_아이디를_모르면_삭제_시_회원_집합은_건드리지_않는다() {
    // given
    when(valueOperations.get("auth:refresh:token-hash")).thenReturn(null);

    // when
    refreshTokenRepository.delete("token-hash");

    // then
    verify(redisTemplate).delete("auth:refresh:token-hash");
    verify(setOperations, never()).remove(anyString(), anyString());
  }

  @Test
  void 회원의_모든_리프레시_토큰을_회수한다() {
    // given
    when(setOperations.members("auth:refresh:member:1"))
        .thenReturn(Set.of("token-hash-1", "token-hash-2"));

    // when
    refreshTokenRepository.deleteByMemberId(1L);

    // then
    verify(redisTemplate).delete("auth:refresh:token-hash-1");
    verify(redisTemplate).delete("auth:refresh:token-hash-2");
    verify(redisTemplate).delete("auth:refresh:member:1");
  }

  @Test
  void 회수할_토큰이_없어도_회원_집합_키는_삭제한다() {
    // given
    when(setOperations.members("auth:refresh:member:1")).thenReturn(Set.of());

    // when
    refreshTokenRepository.deleteByMemberId(1L);

    // then
    verify(redisTemplate).delete("auth:refresh:member:1");
  }

  @Test
  void 레디스_장애로_저장에_실패하면_예외가_발생한다() {
    // given
    Duration ttl = Duration.ofDays(14);
    doThrow(new RedisConnectionFailureException("Redis connection failed"))
        .when(valueOperations)
        .set("auth:refresh:token-hash", "1", ttl);

    // when & then
    assertThatThrownBy(() -> refreshTokenRepository.save("token-hash", 1L, ttl))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE.getMessage());
  }

  @Test
  void 레디스_장애로_삭제에_실패하면_예외가_발생한다() {
    // given
    when(redisTemplate.delete("auth:refresh:token-hash"))
        .thenThrow(new RedisConnectionFailureException("Redis connection failed"));

    // when & then
    assertThatThrownBy(() -> refreshTokenRepository.delete("token-hash"))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE.getMessage());
  }

  @Test
  void 레디스_장애로_일괄_회수에_실패하면_예외가_발생한다() {
    // given
    when(setOperations.members("auth:refresh:member:1"))
        .thenThrow(new RedisConnectionFailureException("Redis connection failed"));

    // when & then
    assertThatThrownBy(() -> refreshTokenRepository.deleteByMemberId(1L))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE.getMessage());
  }

  @Test
  void 레디스_연결에_실패하면_저장소_장애_예외가_발생한다() {
    // given
    when(valueOperations.getAndDelete("auth:refresh:token-hash"))
        .thenThrow(new RedisConnectionFailureException("Redis connection failed"));

    // when & then
    assertThatThrownBy(() -> refreshTokenRepository.consume("token-hash"))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE.getMessage());
  }

  @Test
  void 레디스_커맨드가_시간_초과되면_저장소_장애_예외가_발생한다() {
    // given
    when(valueOperations.getAndDelete("auth:refresh:token-hash"))
        .thenThrow(new QueryTimeoutException("Redis command timed out"));

    // when & then
    assertThatThrownBy(() -> refreshTokenRepository.consume("token-hash"))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE.getMessage());
  }

  @Test
  void 저장된_값이_숫자가_아니면_예외를_전파한다() {
    // given
    when(valueOperations.getAndDelete("auth:refresh:token-hash")).thenReturn("not-a-number");

    // when & then
    assertThatThrownBy(() -> refreshTokenRepository.consume("token-hash"))
        .isInstanceOf(NumberFormatException.class);
  }
}
