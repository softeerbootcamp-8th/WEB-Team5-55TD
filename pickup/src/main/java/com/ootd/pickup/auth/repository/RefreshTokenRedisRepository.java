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
import org.springframework.scheduling.annotation.Async;
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
  private static final String USED_KEY_PREFIX = "auth:refresh:used:";

  /** 이미 소비된 토큰이 다시 들어오면 탈취로 간주한다. 이 판단을 내릴 수 있는 흔적을 남겨두는 기간이며, 그 이후의 재사용은 탐지하지 못한다. */
  private static final Duration REUSE_DETECTION_WINDOW = Duration.ofMinutes(5);

  private final StringRedisTemplate redisTemplate;

  @Async
  @Override
  public void save(String tokenHash, Long memberId, Duration ttl) {
    try {
      redisTemplate.opsForValue().set(key(tokenHash), memberId.toString(), ttl);
      redisTemplate.opsForSet().add(memberKey(memberId), tokenHash);
      redisTemplate.expire(memberKey(memberId), ttl);
    } catch (DataAccessException exception) {
      log.error("리프레시 토큰 저장소 장애로 저장을 생략합니다 - operation=save, memberId={}", memberId, exception);
      throw new PickUpException(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE);
    }
  }

  @Override
  public Optional<Long> consume(String tokenHash) {
    String memberId;

    try {
      memberId = redisTemplate.opsForValue().getAndDelete(key(tokenHash));
      if (memberId != null) {
        redisTemplate.opsForSet().remove(memberKey(Long.valueOf(memberId)), tokenHash);
        redisTemplate.opsForValue().set(usedKey(tokenHash), memberId, REUSE_DETECTION_WINDOW);
      }
    } catch (DataAccessException exception) {
      log.error("리프레시 토큰 저장소 장애로 갱신에 실패했습니다 - operation=consume", exception);
      throw new PickUpException(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE);
    }

    if (memberId == null) {
      revokeIfReused(tokenHash);
      return Optional.empty();
    }

    return Optional.of(Long.valueOf(memberId));
  }

  /**
   * 이미 소비된 토큰이 다시 제시되면 탈취를 의심할 근거가 된다. 정상 사용자라면 발급받은 토큰은 한 번만 쓰므로, 재사용은 다른 누군가가 같은 토큰 값을 들고 있다는
   * 뜻이다. 이 경우 해당 회원의 모든 리프레시 토큰을 회수해 탈취범과 정상 사용자 모두 재로그인하게 만든다.
   */
  private void revokeIfReused(String tokenHash) {
    try {
      String reusedByMemberId = redisTemplate.opsForValue().get(usedKey(tokenHash));
      if (reusedByMemberId == null) {
        return;
      }
      log.warn("이미 소비된 리프레시 토큰이 재사용됐습니다. 전체 세션을 회수합니다 - memberId={}", reusedByMemberId);
      revokeMemberTokens(Long.valueOf(reusedByMemberId));
    } catch (DataAccessException exception) {
      log.error("재사용 탐지 조회 중 저장소 장애가 발생했습니다 - operation=consume", exception);
    }
  }

  @Async
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
      throw new PickUpException(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE);
    }
  }

  @Async
  @Override
  public void deleteByMemberId(Long memberId) {
    try {
      revokeMemberTokens(memberId);
    } catch (DataAccessException exception) {
      log.error(
          "리프레시 토큰 저장소 장애로 회원 토큰 일괄 회수를 생략합니다 - operation=deleteByMemberId, memberId={}",
          memberId,
          exception);
      throw new PickUpException(ExceptionCode.REFRESH_TOKEN_STORE_UNAVAILABLE);
    }
  }

  private void revokeMemberTokens(Long memberId) {
    Set<String> tokenHashes = redisTemplate.opsForSet().members(memberKey(memberId));
    if (tokenHashes != null && !tokenHashes.isEmpty()) {
      tokenHashes.forEach(tokenHash -> redisTemplate.delete(key(tokenHash)));
    }
    redisTemplate.delete(memberKey(memberId));
  }

  private String key(String tokenHash) {
    return KEY_PREFIX + tokenHash;
  }

  private String memberKey(Long memberId) {
    return MEMBER_KEY_PREFIX + memberId;
  }

  private String usedKey(String tokenHash) {
    return USED_KEY_PREFIX + tokenHash;
  }
}
