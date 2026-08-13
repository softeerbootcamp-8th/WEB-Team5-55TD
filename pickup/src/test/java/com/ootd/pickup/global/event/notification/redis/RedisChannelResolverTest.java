package com.ootd.pickup.global.event.notification.redis;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionStartedNotificationEvent;
import com.ootd.pickup.global.event.NotificationEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisChannelResolverTest {

  private final RedisChannelResolver channelResolver = new RedisChannelResolver();

  @Test
  void 채널_이름은_애그리거트_종류와_식별자로_만들어진다() {
    // given
    NotificationEvent event = createEvent(42L);

    // when
    String channel = channelResolver.resolve(event);

    // then
    assertThat(channel).isEqualTo("pickup:notification:AUCTION:42");
  }

  @Test
  void 구독_패턴은_발행_채널과_같은_접두사에_와일드카드를_쓴다() {
    // when
    String pattern = channelResolver.resolvePattern();

    // then
    assertThat(pattern).isEqualTo("pickup:notification:*:*");
  }

  @Test
  void 같은_이벤트로_만든_채널이면_matches가_참을_반환한다() {
    // given
    NotificationEvent event = createEvent(42L);
    String channel = channelResolver.resolve(event);

    // when & then
    assertThat(channelResolver.matches(channel, event)).isTrue();
  }

  @Test
  void 다른_애그리거트_식별자의_채널이면_matches가_거짓을_반환한다() {
    // given
    NotificationEvent event = createEvent(42L);
    String otherChannel = channelResolver.resolve(createEvent(99L));

    // when & then
    assertThat(channelResolver.matches(otherChannel, event)).isFalse();
  }

  @Test
  void aggregateId가_없으면_리터럴_null_채널_대신_예외를_던진다() {
    // given
    NotificationEvent event = createEvent(null);

    // when & then
    assertThatThrownBy(() -> channelResolver.resolve(event))
        .isInstanceOf(IllegalStateException.class);
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
