package com.ootd.pickup.realtime.handler;

import com.ootd.pickup.auction.event.AuctionBidUpdatedEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.realtime.publisher.AuctionBidUpdatedPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionBidUpdatedEventHandler implements EventHandler<AuctionBidUpdatedEvent> {

  private final AuctionBidUpdatedPublisher publisher;

  @Override
  public Class<AuctionBidUpdatedEvent> eventClass() {
    return AuctionBidUpdatedEvent.class;
  }

  @Override
  public void handle(AuctionBidUpdatedEvent event) {
    publisher.publish(event);
  }
}
