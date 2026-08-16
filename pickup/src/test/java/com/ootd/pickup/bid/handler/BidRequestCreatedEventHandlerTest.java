package com.ootd.pickup.bid.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.service.BidRequestProcessingService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BidRequestCreatedEventHandlerTest {

  @Mock private BidRequestProcessingService processingService;

  private BidRequestCreatedEventHandler handler;

  @BeforeEach
  void setUp() {
    handler = new BidRequestCreatedEventHandler(processingService);
  }

  @Test
  void 이벤트_타입은_BidRequestCreatedMessageQueueEvent다() {
    // when & then
    assertThat(handler.eventClass()).isEqualTo(BidRequestCreatedMessageQueueEvent.class);
  }

  @Test
  void 배치_처리는_처리서비스에_그대로_위임한다() {
    // given
    BidRequestCreatedMessageQueueEvent event1 = createEvent(10L);
    BidRequestCreatedMessageQueueEvent event2 = createEvent(11L);
    List<BidRequestCreatedMessageQueueEvent> events = List.of(event1, event2);
    given(processingService.placeBidsForGroup(events)).willReturn(events);

    // when
    List<BidRequestCreatedMessageQueueEvent> done = handler.handleBatch(events);

    // then
    assertThat(done).isEqualTo(events);
    then(processingService).should().placeBidsForGroup(events);
  }

  @Test
  void 단건_처리는_배치로_감싸_위임하고_끝까지_처리되면_예외가_없다() {
    // given
    BidRequestCreatedMessageQueueEvent event = createEvent(10L);
    given(processingService.placeBidsForGroup(List.of(event))).willReturn(List.of(event));

    // when & then
    handler.handle(event);
    then(processingService).should().placeBidsForGroup(List.of(event));
  }

  @Test
  void 단건_처리가_끝까지_반영되지_않으면_예외가_발생한다() {
    // given — 처리서비스가 아무것도 끝내지 못했다고 보고하는 경우(예기치 못한 중단)
    BidRequestCreatedMessageQueueEvent event = createEvent(10L);
    given(processingService.placeBidsForGroup(List.of(event))).willReturn(List.of());

    // when & then
    assertThatThrownBy(() -> handler.handle(event)).isInstanceOf(IllegalStateException.class);
  }

  private BidRequestCreatedMessageQueueEvent createEvent(Long bidRequestId) {
    return new BidRequestCreatedMessageQueueEvent(
        "event-id-" + bidRequestId, bidRequestId, 1L, 2L, 10_500L, LocalDateTime.now());
  }
}
