package com.ootd.pickup.websocket.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.ootd.pickup.auction.event.AuctionBidUpdatedNotificationEvent;
import com.ootd.pickup.bid.websocket.publisher.AuctionBidUpdatedPublisher;
import com.ootd.pickup.bid.websocket.handler.AuctionBidUpdatedEventHandler;
import org.junit.jupiter.api.Test;

class AuctionBidUpdatedEventHandlerTest {

  private final AuctionBidUpdatedPublisher publisher = mock(AuctionBidUpdatedPublisher.class);
  private final AuctionBidUpdatedEventHandler handler =
      new AuctionBidUpdatedEventHandler(publisher);

  @Test
  void 입찰_갱신_이벤트를_WebSocket_publisher에_전달한다() {
    AuctionBidUpdatedNotificationEvent event = mock(AuctionBidUpdatedNotificationEvent.class);

    handler.handle(event);

    then(publisher).should().publish(event);
  }

  @Test
  void 처리할_이벤트_타입을_반환한다() {
    assertThat(handler.eventClass()).isEqualTo(AuctionBidUpdatedNotificationEvent.class);
  }
}
