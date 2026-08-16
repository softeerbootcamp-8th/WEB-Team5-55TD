package com.ootd.pickup.bid.handler;

import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.service.BidRequestProcessingService;
import com.ootd.pickup.global.event.GroupBatchEventHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link BidRequestCreatedMessageQueueEvent} 수신 어댑터.
 *
 * <p>실제 처리(재전달 안전 처리, 배치 낙관적 시도와 건별 폴백 포함)는 전부 {@link BidRequestProcessingService}에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class BidRequestCreatedEventHandler
    implements GroupBatchEventHandler<BidRequestCreatedMessageQueueEvent> {

  private final BidRequestProcessingService processingService;

  @Override
  public Class<BidRequestCreatedMessageQueueEvent> eventClass() {
    return BidRequestCreatedMessageQueueEvent.class;
  }

  @Override
  public List<BidRequestCreatedMessageQueueEvent> handleBatch(
      List<BidRequestCreatedMessageQueueEvent> events) {
    return processingService.placeBidsForGroup(events);
  }
}
