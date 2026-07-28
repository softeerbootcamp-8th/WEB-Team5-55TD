package com.ootd.pickup.auth.repository;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository implements RefreshTokenRepository {
    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String tokenHash, Long memberId, Duration ttl) {
        redisTemplate.opsForValue().set(
            key(tokenHash),
            memberId.toString(),
            ttl
        );
    }

    @Override
    public Optional<Long> consume(String tokenHash) {
        String memberId = redisTemplate.opsForValue()
            .getAndDelete(key(tokenHash));

        if (memberId == null) {
            return Optional.empty();
        }

        return Optional.of(Long.valueOf(memberId));
    }

    @Override
    public void delete(String tokenHash) {
        redisTemplate.delete(key(tokenHash));
    }

    private String key(String tokenHash) {
        return KEY_PREFIX + tokenHash;
    }
}
