package com.ootd.pickup.auth.service;

import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 아이디 기준 고정 윈도우 방식으로 로그인 시도 횟수를 제한한다. 온라인 브루트포스·크리덴셜 스터핑을 늦추는 것이 목적이라 IP가 아닌 아이디로 묶는다 — 같은 아이디를 노리는
 * 공격은 출발 IP를 바꿔도 막아야 하기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {
  private static final String KEY_PREFIX = "auth:login-attempts:";
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration WINDOW = Duration.ofMinutes(5);

  private final StringRedisTemplate redisTemplate;

  /** 시도 한도를 넘었으면 거절한다. 이번 시도는 성공 여부와 무관하게 항상 횟수에 포함시킨다. */
  public void checkAllowed(String loginId) {
    String key = key(loginId);
    Long attempts;
    try {
      attempts = redisTemplate.opsForValue().increment(key);
      if (attempts != null && attempts == 1L) {
        redisTemplate.expire(key, WINDOW);
      }
    } catch (DataAccessException exception) {
      log.error("로그인 시도 제한 저장소 장애로 제한을 생략합니다 - operation=checkAllowed", exception);
      return;
    }

    if (attempts != null && attempts > MAX_ATTEMPTS) {
      log.warn("로그인 시도 횟수를 초과했습니다 - loginId={}, attempts={}", loginId, attempts);
      throw new PickUpException(ExceptionCode.TOO_MANY_LOGIN_ATTEMPTS);
    }
  }

  /** 로그인에 성공하면 그 아이디에 대한 실패 누적을 지운다. */
  public void reset(String loginId) {
    try {
      redisTemplate.delete(key(loginId));
    } catch (DataAccessException exception) {
      log.error("로그인 시도 제한 초기화에 실패했습니다 - operation=reset", exception);
    }
  }

  private String key(String loginId) {
    return KEY_PREFIX + loginId;
  }
}
