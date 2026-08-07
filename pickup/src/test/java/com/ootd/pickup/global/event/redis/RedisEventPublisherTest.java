package com.ootd.pickup.global.event.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionStartedNotificationEvent;
import com.ootd.pickup.global.event.NotificationEvent;
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
 * {@code convertAndSend}로 나가는 채널·메시지가 실제로 {@link NotificationChannelResolver}/{@link
 * NotificationEnvelopeReader}와 앞뒤가 맞는지 확인한다.
 *
 * <p>{@link ObjectMapper}와 {@link NotificationChannelResolver}는 실제 구현을 그대로 쓴다 — 이 테스트가 검증하려는 건 "발행이
 * 실제 봉투 규격을 지키는가"이지 "Mock이 어떻게 응답하는가"가 아니라서, 봉투를 목으로 대체하면 검증 의미가 없어진다.
 */
@ExtendWith(MockitoExtension.class)
class RedisEventPublisherTest {

  @Mock private StringRedisTemplate redisTemplate;

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final NotificationChannelResolver channelResolver = new NotificationChannelResolver();
  private final NotificationEnvelopeReader envelopeReader =
      new NotificationEnvelopeReader(objectMapper);

  private RedisEventPublisher redisEventPublisher;

  @BeforeEach
  void setUp() {
    redisEventPublisher = new RedisEventPublisher(redisTemplate, objectMapper, channelResolver);
  }

  @Test
  void 이벤트를_발행하면_NotificationChannelResolver가_계산한_채널로_보낸다() {
    // given
    NotificationEvent event = createEvent(42L);

    // when
    redisEventPublisher.publish(event);

    // then
    ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
    then(redisTemplate).should().convertAndSend(channelCaptor.capture(), any());
    assertThat(channelCaptor.getValue()).isEqualTo(channelResolver.resolve(event));
  }

  @Test
  void 이벤트를_발행하면_봉투를_열었을_때_원본_이벤트가_그대로_복원된다() {
    // given
    NotificationEvent event = createEvent(42L);

    // when
    redisEventPublisher.publish(event);

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
    redisEventPublisher.publish(first);
    redisEventPublisher.publish(second);

    // then
    ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
    then(redisTemplate).should(times(2)).convertAndSend(channelCaptor.capture(), any());
    assertThat(channelCaptor.getAllValues()).doesNotHaveDuplicates();
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
