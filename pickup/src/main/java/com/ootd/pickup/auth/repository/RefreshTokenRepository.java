package com.ootd.pickup.auth.repository;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenRepository {
  void save(String tokenHash, Long memberId, Duration ttl);

  Optional<Long> consume(String tokenHash);

  void delete(String tokenHash);
}
