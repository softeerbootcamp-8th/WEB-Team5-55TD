package com.ootd.pickup.realtime.config;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/*
Spring STOMP 메시지가 채널을 통과하기 전후에 개입할 수 있는 interceptor
STOMP frame을 검사하여 필요한 명령만 허용하고, 공개 경매 topic만 구독할 수 있게
 */
@Component
public class AuctionSubscriptionInterceptor implements ChannelInterceptor {

  private static final Pattern AUCTION_TOPIC = Pattern.compile("^/topic/auctions/[1-9]\\d*$");
  private static final Set<StompCommand> ALLOWED_COMMANDS =
      EnumSet.of(
          StompCommand.CONNECT,
          StompCommand.STOMP,
          StompCommand.SUBSCRIBE,
          StompCommand.UNSUBSCRIBE,
          StompCommand.DISCONNECT);

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    StompCommand command = accessor.getCommand();

    if (command == null && accessor.getMessageType() == SimpMessageType.HEARTBEAT) {
      return message;
    }
    if (command == null || !ALLOWED_COMMANDS.contains(command)) {
      throw new MessageDeliveryException("허용되지 않은 STOMP 명령입니다.");
    }
    if (command == StompCommand.SUBSCRIBE) {
      validateSubscription(accessor.getDestination());
    }
    return message;
  }

  private void validateSubscription(String destination) {
    if (destination == null || !AUCTION_TOPIC.matcher(destination).matches()) {
      throw new MessageDeliveryException("허용되지 않은 구독 경로입니다.");
    }
  }
}
