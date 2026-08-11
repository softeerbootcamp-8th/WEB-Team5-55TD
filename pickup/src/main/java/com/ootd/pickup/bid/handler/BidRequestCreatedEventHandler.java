package com.ootd.pickup.bid.handler;

import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.service.BidRequestProcessingService;
import com.ootd.pickup.bid.service.BidRequestStatusService;
import com.ootd.pickup.global.event.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * {@link BidRequestCreatedMessageQueueEvent} 수신 어댑터.
 *
 * <p>실제 처리는 재전달에 안전한 {@link BidRequestProcessingService}에 위임한다. {@link
 * DataIntegrityViolationException}만 여기서 잡는다 — {@code bid.bid_request_id} 유니크 제약 충돌은 이 요청이 이미 성공적으로
 * 처리됐다는 신호이지 실패가 아니므로, 잡지 않으면 {@code SQSEventConsumer}가 알 수 없는 실패로 보고 메시지 그룹을 막는다 ({@code
 * SettlementEventHandler}와 동일한 이유).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidRequestCreatedEventHandler
    implements EventHandler<BidRequestCreatedMessageQueueEvent> {

  private final BidRequestProcessingService processingService;
  private final BidRequestStatusService statusService;

  @Override
  public Class<BidRequestCreatedMessageQueueEvent> eventClass() {
    return BidRequestCreatedMessageQueueEvent.class;
  }

  @Override
  public void handle(BidRequestCreatedMessageQueueEvent event) {
    try {
      processingService.process(event);
    } catch (DataIntegrityViolationException exception) {
      log.info(
          "재전달 사이 이미 처리된 입찰 요청이라 성공으로 정리함 - bidRequestId={}, eventId={}",
          event.bidRequestId(),
          event.eventId());
      statusService.markSucceeded(event.bidRequestId());
    }
  }
}
