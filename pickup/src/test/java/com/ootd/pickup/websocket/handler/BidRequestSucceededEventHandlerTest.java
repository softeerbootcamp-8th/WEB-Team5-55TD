package com.ootd.pickup.websocket.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.ootd.pickup.auction.event.BidRequestSucceededNotificationEvent;
import com.ootd.pickup.bid.websocket.handler.BidRequestSucceededEventHandler;
import com.ootd.pickup.bid.websocket.publisher.BidRequestSucceededPublisher;
import org.junit.jupiter.api.Test;

class BidRequestSucceededEventHandlerTest {

  private final BidRequestSucceededPublisher publisher = mock(BidRequestSucceededPublisher.class);
  private final BidRequestSucceededEventHandler handler =
      new BidRequestSucceededEventHandler(publisher);

  @Test
  void 입찰_요청_성공_이벤트를_WebSocket_publisher에_전달한다() {
    BidRequestSucceededNotificationEvent event = mock(BidRequestSucceededNotificationEvent.class);

    handler.handle(event);

    then(publisher).should().publish(event);
  }

  @Test
  void 처리할_이벤트_타입을_반환한다() {
    assertThat(handler.eventClass()).isEqualTo(BidRequestSucceededNotificationEvent.class);
  }
}
