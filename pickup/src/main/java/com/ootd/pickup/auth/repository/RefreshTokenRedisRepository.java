package com.ootd.pickup.auth.repository;

import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 토큰 값(해시) -> 회원 ID로만 저장하면 특정 토큰 하나는 지울 수 있어도 "이 회원의 모든 토큰"은 찾을 수 없다. 회원 탈퇴 시 기기 전체의 리프레시 토큰을 회수해야
 * 하므로, 회원별로 발급한 토큰 해시 목록을 {@code auth:refresh:member:{memberId}} 집합에 함께 유지한다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository implements RefreshTokenRepository {
  private static final String KEY_PREFIX = "auth:refresh:";
  private static final String MEMBER_KEY_PREFIX = "auth:refresh:member:";

  private final StringRedisTemplate redisTemplate;

  @Override
  public void save(String tokenHash, Long memberId, Duration ttl) {
    try {
      redisTemplate.opsForValue().set(key(tokenHash), memberId.toString(), ttl);
      redisTemplate.opsForSet().add(memberKey(memberId), tokenHash);
      redisTemplate.expire(memberKey(memberId), ttl);
    } catch (DataAccessException exception) {
      log.error("리프레시 토큰 저장소 장애로 저장을 생략합니다 - operation=save, memberId={}", memberId, exception);
    }
  }

  @Override
  public Optional<Long> consume(String tokenHash) {
    String memberId;

    try {
      memberId = redisTemplate.opsForValue().getAndDelete(key(tokenHash));
      if (memberId != null) {
        redisTemplate.opsForSet().remove(memberKey(Long.valueOf(memberId)), tokenHash);
      }
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
      String memberId = redisTemplate.opsForValue().get(key(tokenHash));
      redisTemplate.delete(key(tokenHash));
      if (memberId != null) {
        redisTemplate.opsForSet().remove(memberKey(Long.valueOf(memberId)), tokenHash);
      }
    } catch (DataAccessException exception) {
      log.error("리프레시 토큰 저장소 장애로 삭제를 생략합니다 - operation=delete", exception);
    }
  }

  @Override
  public void deleteByMemberId(Long memberId) {
    try {
      Set<String> tokenHashes = redisTemplate.opsForSet().members(memberKey(memberId));
      if (tokenHashes != null && !tokenHashes.isEmpty()) {
        tokenHashes.forEach(tokenHash -> redisTemplate.delete(key(tokenHash)));
      }
      redisTemplate.delete(memberKey(memberId));
    } catch (DataAccessException exception) {
      log.error(
          "리프레시 토큰 저장소 장애로 회원 토큰 일괄 회수를 생략합니다 - operation=deleteByMemberId, memberId={}",
          memberId,
          exception);
    }
  }

  private String key(String tokenHash) {
    return KEY_PREFIX + tokenHash;
  }

  private String memberKey(Long memberId) {
    return MEMBER_KEY_PREFIX + memberId;
  }
}
