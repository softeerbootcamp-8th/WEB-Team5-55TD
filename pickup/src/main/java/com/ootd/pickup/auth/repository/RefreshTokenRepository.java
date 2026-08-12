package com.ootd.pickup.auth.repository;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenRepository {
  void save(String tokenHash, Long memberId, Duration ttl);

  Optional<Long> consume(String tokenHash);

  void delete(String tokenHash);

  /** 회원이 가진 모든 기기의 리프레시 토큰을 회수한다. 회원 탈퇴 시 재로그인을 막기 위해 쓴다. */
  void deleteByMemberId(Long memberId);
}
