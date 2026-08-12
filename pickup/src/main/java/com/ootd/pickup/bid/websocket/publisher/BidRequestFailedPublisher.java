package com.ootd.pickup.bid.websocket.publisher;

import com.ootd.pickup.bid.event.BidRequestFailedNotificationEvent;
import com.ootd.pickup.bid.websocket.dto.BidRequestFailedMessage;
import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 입찰 요청 실패를 요청한 회원 본인에게만 전달한다.
 *
 * <p>{@code convertAndSendToUser}는 {@code destination}에 {@code setUserDestinationPrefix("/user")}가
 * 내부적으로 붙여지므로, 여기서는 그 접두어를 뺀 상대 경로만 넘긴다. 클라이언트는 {@code /user/queue/bid-requests}를 구독한다.
 */
@Component
@RequiredArgsConstructor
public class BidRequestFailedPublisher {

  private static final String BID_REQUESTS_QUEUE_DESTINATION = "/queue/bid-requests";

  private final SimpMessagingTemplate messagingTemplate;
  private final RealtimeNotificationMetrics metrics;

  public void publish(BidRequestFailedNotificationEvent event) {
    BidRequestFailedMessage message = BidRequestFailedMessage.fromEvent(event);
    try {
      messagingTemplate.convertAndSendToUser(
          String.valueOf(event.memberId()), BID_REQUESTS_QUEUE_DESTINATION, message);
      metrics.recordBrokerPublishSuccess(event.eventType());
    } catch (RuntimeException exception) {
      metrics.recordBrokerPublishFailure(event.eventType());
      throw exception;
    }
  }
}
