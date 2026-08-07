package com.ootd.pickup.bid.websocket.publisher;

import com.ootd.pickup.auction.event.AuctionBidUpdatedNotificationEvent;
import com.ootd.pickup.bid.websocket.dto.AuctionBidUpdatedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionBidUpdatedPublisher {

  private static final String AUCTION_TOPIC_PREFIX = "/topic/auctions/";

  private final SimpMessagingTemplate messagingTemplate;

  public void publish(AuctionBidUpdatedNotificationEvent event) {
    messagingTemplate.convertAndSend(
        AUCTION_TOPIC_PREFIX + event.auctionId(), AuctionBidUpdatedMessage.fromEvent(event));
  }
}
