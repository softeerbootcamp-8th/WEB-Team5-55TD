package com.ootd.pickup.auth.repository;

import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
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
public class RefreshTokenRedisRepository implements RefreshTokenRepository {
  private static final String KEY_PREFIX = "auth:refresh:";

  private final StringRedisTemplate redisTemplate;

  @Override
  public void save(String tokenHash, Long memberId, Duration ttl) {
    try {
      redisTemplate.opsForValue().set(key(tokenHash), memberId.toString(), ttl);
    } catch (DataAccessException exception) {
      log.error("리프레시 토큰 저장소 장애로 저장을 생략합니다 - operation=save, memberId={}", memberId, exception);
    }
  }

  @Override
  public Optional<Long> consume(String tokenHash) {
    String memberId;

    try {
      memberId = redisTemplate.opsForValue().getAndDelete(key(tokenHash));
    } catch (DataAccessException exception) {
      log.error("리프레시 토큰 저장소 장애로 갱신에 실패했습니다 - operation=consume", exception);
      throw new PickUpException(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE);
    }

    if (memberId == null) {
      return Optional.empty();
    }

    return Optional.of(Long.valueOf(memberId));
  }

  @Override
  public void delete(String tokenHash) {
    try {
      redisTemplate.delete(key(tokenHash));
    } catch (DataAccessException exception) {
      log.error("리프레시 토큰 저장소 장애로 삭제를 생략합니다 - operation=delete", exception);
    }
  }

  private String key(String tokenHash) {
    return KEY_PREFIX + tokenHash;
  }
}
