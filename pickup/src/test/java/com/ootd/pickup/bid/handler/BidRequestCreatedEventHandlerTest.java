package com.ootd.pickup.bid.handler;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.service.BidRequestProcessingService;
import com.ootd.pickup.bid.service.BidRequestStatusService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class BidRequestCreatedEventHandlerTest {

  @Mock private BidRequestProcessingService processingService;
  @Mock private BidRequestStatusService statusService;

  private BidRequestCreatedEventHandler handler;

  @BeforeEach
  void setUp() {
    handler = new BidRequestCreatedEventHandler(processingService, statusService);
  }

  @Test
  void 정상_처리되면_상태서비스를_추가로_호출하지_않는다() {
    // given
    BidRequestCreatedMessageQueueEvent event = createEvent();

    // when
    handler.handle(event);

    // then
    then(processingService).should().process(event);
    then(statusService).shouldHaveNoInteractions();
  }

  @Test
  void 유니크_제약_충돌은_이미_성공한_것으로_처리한다() {
    // given
    BidRequestCreatedMessageQueueEvent event = createEvent();
    willThrow(new DataIntegrityViolationException("duplicate"))
        .given(processingService)
        .process(event);

    // when
    handler.handle(event);

    // then
    then(statusService).should().markSucceeded(event.bidRequestId());
  }

  private BidRequestCreatedMessageQueueEvent createEvent() {
    return new BidRequestCreatedMessageQueueEvent(
        "event-id", 10L, 1L, 2L, 10_500L, LocalDateTime.now());
  }
}
