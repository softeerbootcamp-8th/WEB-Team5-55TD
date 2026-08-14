package com.ootd.pickup.auth.repository;

import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AccessTokenDenylistRedisRepository implements AccessTokenDenylistRepository {
  private static final String KEY_PREFIX = "auth:access:denylist:";

  private final StringRedisTemplate redisTemplate;

  @Override
  public void denylistMember(Long memberId, Duration ttl) {
    if (ttl.isNegative() || ttl.isZero()) {
      return;
    }
    try {
      redisTemplate.opsForValue().set(key(memberId), "1", ttl);
    } catch (DataAccessException exception) {
      log.error(
          "액세스 토큰 거부 목록 등록에 실패했습니다 - operation=denylistMember, memberId={}", memberId, exception);
      throw new PickUpException(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE);
    }
  }

  /** 조회 실패 시 인증 경로 전체가 막히지 않도록, 저장소 장애에서는 거부하지 않은 것으로 취급한다(fail-open). */
  @Override
  public boolean isDenylisted(Long memberId) {
    try {
      return Boolean.TRUE.equals(redisTemplate.hasKey(key(memberId)));
    } catch (DataAccessException exception) {
      log.error(
          "액세스 토큰 거부 목록 조회에 실패해 통과시킵니다 - operation=isDenylisted, memberId={}", memberId, exception);
      return false;
    }
  }

  private String key(Long memberId) {
    return KEY_PREFIX + memberId;
  }
}
