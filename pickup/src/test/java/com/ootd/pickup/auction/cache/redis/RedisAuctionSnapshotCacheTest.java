package com.ootd.pickup.auction.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.ootd.pickup.auction.cache.AuctionSnapshot;
import com.ootd.pickup.auction.cache.AuctionSnapshotCacheProperties;
import com.ootd.pickup.auction.domain.AuctionStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 이 캐시는 밑져야 본전인 사전 필터용이라, Redis 장애가 조회·저장 실패로 이어져도 예외가 호출자까지 전파되면 안 된다는 계약을 검증한다. */
@ExtendWith(MockitoExtension.class)
class RedisAuctionSnapshotCacheTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final AuctionSnapshotCacheProperties properties =
      new AuctionSnapshotCacheProperties(Duration.ofSeconds(30));

  private RedisAuctionSnapshotCache cache;

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    cache = new RedisAuctionSnapshotCache(redisTemplate, objectMapper, properties);
  }

  @Test
  void 저장한_스냅샷을_그대로_조회한다() {
    // given
    AuctionSnapshot snapshot = createSnapshot(1L);
    cache.put(snapshot);
    ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
    then(valueOperations)
        .should()
        .set(eq("pickup:auction-snapshot:1"), valueCaptor.capture(), eq(Duration.ofSeconds(30)));
    given(valueOperations.get("pickup:auction-snapshot:1")).willReturn(valueCaptor.getValue());

    // when
    Optional<AuctionSnapshot> found = cache.find(1L);

    // then
    assertThat(found).contains(snapshot);
  }

  @Test
  void 존재하지_않는_경매의_스냅샷은_빈_값을_반환한다() {
    // given
    given(valueOperations.get("pickup:auction-snapshot:999")).willReturn(null);

    // when
    Optional<AuctionSnapshot> found = cache.find(999L);

    // then
    assertThat(found).isEmpty();
  }

  @Test
  void 저장할_때_설정된_TTL을_사용한다() {
    // given
    AuctionSnapshot snapshot = createSnapshot(1L);

    // when
    cache.put(snapshot);

    // then
    then(valueOperations).should().set(anyString(), anyString(), eq(Duration.ofSeconds(30)));
  }

  @Test
  void 저장_중_Redis_오류가_나도_예외를_전파하지_않는다() {
    // given
    AuctionSnapshot snapshot = createSnapshot(1L);
    willThrow(new IllegalStateException("redis down"))
        .given(valueOperations)
        .set(anyString(), anyString(), any(Duration.class));

    // when & then
    cache.put(snapshot);
  }

  @Test
  void 조회_중_Redis_오류가_나면_예외를_전파하지_않고_빈_값을_반환한다() {
    // given
    given(valueOperations.get(anyString())).willThrow(new IllegalStateException("redis down"));

    // when
    Optional<AuctionSnapshot> found = cache.find(1L);

    // then
    assertThat(found).isEmpty();
  }

  private AuctionSnapshot createSnapshot(Long auctionId) {
    return new AuctionSnapshot(
        auctionId, 10_000L, 500L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1), 9L);
  }
}
