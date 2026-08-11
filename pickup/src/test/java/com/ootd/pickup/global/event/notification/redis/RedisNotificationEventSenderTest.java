package com.ootd.pickup.global.event.notification.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionStartedNotificationEvent;
import com.ootd.pickup.global.event.NotificationEvent;
import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code convertAndSend}로 나가는 채널·메시지가 실제로 {@link RedisChannelResolver}/{@link RedisEnvelopeReader}와
 * 앞뒤가 맞는지 확인한다.
 *
 * <p>{@link ObjectMapper}와 {@link RedisChannelResolver}는 실제 구현을 그대로 쓴다 — 이 테스트가 검증하려는 건 "발행이 실제 봉투
 * 규격을 지키는가"이지 "Mock이 어떻게 응답하는가"가 아니라서, 봉투를 목으로 대체하면 검증 의미가 없어진다.
 */
@ExtendWith(MockitoExtension.class)
class RedisNotificationEventSenderTest {

  @Mock private StringRedisTemplate redisTemplate;

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final RedisChannelResolver channelResolver = new RedisChannelResolver();
  private final RedisEnvelopeReader envelopeReader = new RedisEnvelopeReader(objectMapper);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RealtimeNotificationMetrics metrics =
      new RealtimeNotificationMetrics(meterRegistry);

  private RedisNotificationEventSender redisNotificationEventSender;

  @BeforeEach
  void setUp() {
    given(redisTemplate.convertAndSend(anyString(), anyString())).willReturn(1L);
    redisNotificationEventSender =
        new RedisNotificationEventSender(redisTemplate, objectMapper, channelResolver, metrics);
  }

  @Test
  void 이벤트를_발행하면_RedisChannelResolver가_계산한_채널로_보낸다() {
    // given
    NotificationEvent event = createEvent(42L);

    // when
    redisNotificationEventSender.send(event);

    // then
    ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
    then(redisTemplate).should().convertAndSend(channelCaptor.capture(), any());
    assertThat(channelCaptor.getValue()).isEqualTo(channelResolver.resolve(event));
    assertThat(publishCount("success")).isEqualTo(1);
  }

  @Test
  void 이벤트를_발행하면_봉투를_열었을_때_원본_이벤트가_그대로_복원된다() {
    // given
    NotificationEvent event = createEvent(42L);

    // when
    redisNotificationEventSender.send(event);

    // then
    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
    then(redisTemplate).should().convertAndSend(any(), messageCaptor.capture());
    NotificationEvent restored =
        envelopeReader.read(messageCaptor.getValue().getBytes(StandardCharsets.UTF_8));
    assertThat(restored).isEqualTo(event);
  }

  @Test
  void 서로_다른_이벤트를_발행하면_채널도_서로_다르다() {
    // given
    NotificationEvent first = createEvent(1L);
    NotificationEvent second = createEvent(2L);

    // when
    redisNotificationEventSender.send(first);
    redisNotificationEventSender.send(second);

    // then
    ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
    then(redisTemplate).should(times(2)).convertAndSend(channelCaptor.capture(), any());
    assertThat(channelCaptor.getAllValues()).doesNotHaveDuplicates();
  }

  @Test
  void Redis_발행이_실패하면_실패를_기록하고_예외를_전파한다() {
    NotificationEvent event = createEvent(42L);
    given(redisTemplate.convertAndSend(anyString(), anyString()))
        .willThrow(new IllegalStateException("redis unavailable"));

    assertThatThrownBy(() -> redisNotificationEventSender.send(event))
        .isInstanceOf(IllegalStateException.class);
    assertThat(publishCount("failure")).isEqualTo(1);
  }

  @Test
  void Redis_구독자가_없으면_별도_결과로_기록한다() {
    NotificationEvent event = createEvent(42L);
    given(redisTemplate.convertAndSend(anyString(), anyString())).willReturn(0L);

    redisNotificationEventSender.send(event);

    assertThat(publishCount("no_subscribers")).isEqualTo(1);
  }

  private double publishCount(String outcome) {
    return meterRegistry
        .get("pickup.redis.notification.publish")
        .tags("outcome", outcome, "event_type", "AUCTION_STARTED")
        .counter()
        .count();
  }

  private AuctionStartedNotificationEvent createEvent(Long auctionId) {
    return new AuctionStartedNotificationEvent(
        UUID.randomUUID().toString(),
        auctionId,
        100L,
        10_000L,
        15_000L,
        null,
        null,
        AuctionStatus.ONGOING,
        LocalDateTime.now(),
        LocalDateTime.now(),
        LocalDateTime.now(),
        LocalDateTime.now());
  }
}
