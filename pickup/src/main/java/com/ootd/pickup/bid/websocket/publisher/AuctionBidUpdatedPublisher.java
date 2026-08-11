package com.ootd.pickup.bid.websocket.publisher;

import com.ootd.pickup.auction.event.AuctionBidUpdatedNotificationEvent;
import com.ootd.pickup.bid.websocket.dto.AuctionBidUpdatedMessage;
import com.ootd.pickup.global.observability.RealtimeNotificationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionBidUpdatedPublisher {

  private static final String AUCTION_TOPIC_PREFIX = "/topic/auctions/";

  private final SimpMessagingTemplate messagingTemplate;
  private final RealtimeNotificationMetrics metrics;

  public void publish(AuctionBidUpdatedNotificationEvent event) {
    AuctionBidUpdatedMessage message = AuctionBidUpdatedMessage.fromEvent(event);
    try {
      messagingTemplate.convertAndSend(AUCTION_TOPIC_PREFIX + event.auctionId(), message);
      // 이 성공은 Broker channel 전달 성공일 뿐, 브라우저의 실제 수신·렌더링까지 보장하지 않는다.
      metrics.recordBrokerPublishSuccess(event.eventType());
      log.debug(
          "웹소켓으로 입찰 갱신을 브로드캐스트했습니다 - auctionId={}, eventType={}",
          event.auctionId(),
          event.eventType());
    } catch (RuntimeException exception) {
      metrics.recordBrokerPublishFailure(event.eventType());
      throw exception;
    }
  }
}
