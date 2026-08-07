package com.ootd.pickup.bid.websocket.publisher;

import com.ootd.pickup.auction.event.AuctionBidUpdatedNotificationEvent;
import com.ootd.pickup.bid.websocket.handler.AuctionBidUpdatedPublisher;
import com.ootd.pickup.global.event.EventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionBidUpdatedEventHandler
    implements EventHandler<AuctionBidUpdatedNotificationEvent> {

  private final AuctionBidUpdatedPublisher publisher;

  @Override
  public Class<AuctionBidUpdatedNotificationEvent> eventClass() {
    return AuctionBidUpdatedNotificationEvent.class;
  }

  @Override
  public void handle(AuctionBidUpdatedNotificationEvent event) {
    publisher.publish(event);
  }
}
