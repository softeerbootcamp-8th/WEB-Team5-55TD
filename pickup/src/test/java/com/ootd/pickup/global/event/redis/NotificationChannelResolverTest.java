package com.ootd.pickup.global.event.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationChannelResolverTest {

  private final NotificationChannelResolver channelResolver = new NotificationChannelResolver();
  @Mock private NotificationEvent event;

  @Test
  void 채널_이름은_애그리거트_종류와_식별자로_만들어진다() {
    given(event.aggregateType()).willReturn(AggregateType.AUCTION);
    given(event.aggregateId()).willReturn(42L);

    assertThat(channelResolver.resolve(event)).isEqualTo("pickup:notification:AUCTION:42");
  }

  @Test
  void 구독_패턴은_발행_채널과_같은_접두사에_와일드카드를_쓴다() {
    assertThat(channelResolver.resolvePattern()).isEqualTo("pickup:notification:*:*");
  }

  @Test
  void 다른_애그리거트_식별자의_채널은_일치하지_않는다() {
    given(event.aggregateType()).willReturn(AggregateType.AUCTION);
    given(event.aggregateId()).willReturn(42L);

    assertThat(channelResolver.matches("pickup:notification:AUCTION:99", event)).isFalse();
  }

  @Test
  void aggregateId가_없으면_null_채널_대신_예외를_던진다() {
    given(event.aggregateId()).willReturn(null);
    given(event.eventType()).willReturn("AUCTION_BID_UPDATED");

    assertThatThrownBy(() -> channelResolver.resolve(event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AUCTION_BID_UPDATED");
  }
}
