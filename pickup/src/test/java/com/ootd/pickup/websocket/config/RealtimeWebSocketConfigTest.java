package com.ootd.pickup.websocket.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.scheduling.TaskScheduler;

class RealtimeWebSocketConfigTest {

  @Test
  void Simple_Broker가_topic과_회원별_queue를_모두_처리한다() {
    // given
    SubscribableChannel clientInboundChannel = new ExecutorSubscribableChannel();
    MessageChannel clientOutboundChannel = new ExecutorSubscribableChannel();
    TestMessageBrokerRegistry registry =
        new TestMessageBrokerRegistry(clientInboundChannel, clientOutboundChannel);
    RealtimeWebSocketConfig config =
        new RealtimeWebSocketConfig(
            mock(RealtimeWebSocketProperties.class),
            mock(TaskScheduler.class),
            mock(AuctionSubscriptionInterceptor.class),
            mock(WebSocketAuthHandshakeInterceptor.class),
            mock(MemberHandshakeHandler.class));

    // when
    config.configureMessageBroker(registry);

    // then
    assertThat(registry.simpleBrokerDestinationPrefixes()).containsExactly("/topic", "/queue");
  }

  private static class TestMessageBrokerRegistry extends MessageBrokerRegistry {

    private final SubscribableChannel brokerChannel = new ExecutorSubscribableChannel();

    private TestMessageBrokerRegistry(
        SubscribableChannel clientInboundChannel, MessageChannel clientOutboundChannel) {
      super(clientInboundChannel, clientOutboundChannel);
    }

    private Iterable<String> simpleBrokerDestinationPrefixes() {
      SimpleBrokerMessageHandler simpleBroker = getSimpleBroker(brokerChannel);
      return simpleBroker.getDestinationPrefixes();
    }
  }
}
