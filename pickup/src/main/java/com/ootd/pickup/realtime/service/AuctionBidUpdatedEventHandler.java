package com.ootd.pickup.realtime.service;

import com.ootd.pickup.auction.event.AuctionBidUpdatedEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.realtime.dto.AuctionBidUpdatedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionBidUpdatedEventHandler implements EventHandler<AuctionBidUpdatedEvent> {

  private static final String AUCTION_TOPIC_PREFIX = "/topic/auctions/";

  private final SimpMessagingTemplate messagingTemplate;

  @Override
  public Class<AuctionBidUpdatedEvent> eventClass() {
    return AuctionBidUpdatedEvent.class;
  }

  @Override
  public void handle(AuctionBidUpdatedEvent event) {
    messagingTemplate.convertAndSend(
        AUCTION_TOPIC_PREFIX + event.auctionId(), AuctionBidUpdatedMessage.fromEvent(event));
  }
}
