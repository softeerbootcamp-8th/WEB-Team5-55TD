package com.ootd.pickup.bid.websocket.handler;

import com.ootd.pickup.auction.event.BidRequestSucceededNotificationEvent;
import com.ootd.pickup.bid.websocket.publisher.BidRequestSucceededPublisher;
import com.ootd.pickup.global.event.EventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidRequestSucceededEventHandler
    implements EventHandler<BidRequestSucceededNotificationEvent> {

  private final BidRequestSucceededPublisher publisher;

  @Override
  public Class<BidRequestSucceededNotificationEvent> eventClass() {
    return BidRequestSucceededNotificationEvent.class;
  }

  @Override
  public void handle(BidRequestSucceededNotificationEvent event) {
    publisher.publish(event);
  }
}
