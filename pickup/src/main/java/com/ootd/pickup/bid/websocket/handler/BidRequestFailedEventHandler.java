package com.ootd.pickup.bid.websocket.handler;

import com.ootd.pickup.bid.event.BidRequestFailedNotificationEvent;
import com.ootd.pickup.bid.websocket.publisher.BidRequestFailedPublisher;
import com.ootd.pickup.global.event.EventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidRequestFailedEventHandler
    implements EventHandler<BidRequestFailedNotificationEvent> {

  private final BidRequestFailedPublisher publisher;

  @Override
  public Class<BidRequestFailedNotificationEvent> eventClass() {
    return BidRequestFailedNotificationEvent.class;
  }

  @Override
  public void handle(BidRequestFailedNotificationEvent event) {
    publisher.publish(event);
  }
}
