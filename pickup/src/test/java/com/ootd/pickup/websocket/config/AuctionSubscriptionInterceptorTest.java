package com.ootd.pickup.websocket.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class AuctionSubscriptionInterceptorTest {

  private final AuctionSubscriptionInterceptor interceptor = new AuctionSubscriptionInterceptor();
  private final MessageChannel channel = (message, timeout) -> true;

  @Test
  void heartbeat는_통과한다() {
    Message<?> message = message(SimpMessageType.HEARTBEAT, null, null);

    assertThat(interceptor.preSend(message, channel)).isSameAs(message);
  }

  @Test
  void 허용된_STOMP_command는_통과한다() {
    for (StompCommand command : StompCommand.values()) {
      if (command == StompCommand.SUBSCRIBE
          || command == StompCommand.CONNECT
          || command == StompCommand.STOMP
          || command == StompCommand.UNSUBSCRIBE
          || command == StompCommand.DISCONNECT) {
        Message<?> message = message(null, command, "/topic/auctions/123");
        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
      }
    }
  }

  @Test
  void command가_없으면_거부한다() {
    Message<?> message = message(null, null, null);

    assertThatThrownBy(() -> interceptor.preSend(message, channel))
        .isInstanceOf(MessageDeliveryException.class);
  }

  @Test
  void 허용되지_않은_command는_거부한다() {
    Message<?> message = message(null, StompCommand.SEND, null);

    assertThatThrownBy(() -> interceptor.preSend(message, channel))
        .isInstanceOf(MessageDeliveryException.class);
  }

  @Test
  void 경매_topic만_subscribe할_수_있다() {
    assertThat(
            interceptor.preSend(
                message(null, StompCommand.SUBSCRIBE, "/topic/auctions/1"), channel))
        .isNotNull();

    assertThatThrownBy(
            () ->
                interceptor.preSend(
                    message(null, StompCommand.SUBSCRIBE, "/topic/auctions/0"), channel))
        .isInstanceOf(MessageDeliveryException.class);
    assertThatThrownBy(
            () ->
                interceptor.preSend(
                    message(null, StompCommand.SUBSCRIBE, "/queue/auctions/1"), channel))
        .isInstanceOf(MessageDeliveryException.class);
    assertThatThrownBy(
            () -> interceptor.preSend(message(null, StompCommand.SUBSCRIBE, null), channel))
        .isInstanceOf(MessageDeliveryException.class);
  }

  private Message<?> message(
      SimpMessageType messageType, StompCommand command, String destination) {
    if (messageType == null && command == null) {
      return MessageBuilder.withPayload(new byte[0]).build();
    }
    StompHeaderAccessor accessor =
        messageType == SimpMessageType.HEARTBEAT
            ? StompHeaderAccessor.createForHeartbeat()
            : StompHeaderAccessor.create(command);
    accessor.setDestination(destination);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
