package com.ootd.pickup.auth.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RefreshTokenRedisRepositoryTest {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RefreshTokenRedisRepository refreshTokenRepository;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        refreshTokenRepository = new RefreshTokenRedisRepository(redisTemplate);
    }

    @Test
    void 리프레시_토큰_해시와_회원_아이디를_저장한다() {
        Duration ttl = Duration.ofDays(14);

        refreshTokenRepository.save("token-hash", 1L, ttl);

        verify(valueOperations).set("auth:refresh:token-hash", "1", ttl);
    }

    @Test
    void 리프레시_토큰을_조회하면서_삭제한다() {
        when(valueOperations.getAndDelete("auth:refresh:token-hash"))
            .thenReturn("1");

        assertThat(refreshTokenRepository.consume("token-hash"))
            .contains(1L);

        verify(valueOperations).getAndDelete("auth:refresh:token-hash");
    }

    @Test
    void 리프레시_토큰을_삭제한다() {
        refreshTokenRepository.delete("token-hash");

        verify(redisTemplate).delete("auth:refresh:token-hash");
    }
}
