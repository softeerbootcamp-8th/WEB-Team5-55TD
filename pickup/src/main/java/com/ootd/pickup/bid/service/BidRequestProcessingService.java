package com.ootd.pickup.bid.service;

import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.domain.BidRequestStatus;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.repository.BidRequestRepository;
import com.ootd.pickup.global.exception.PickUpException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link BidRequestCreatedMessageQueueEvent} 처리 — 실제 입찰(락 획득·업무 규칙 검증·저장)을 수행한다.
 *
 * <p>{@link #process}는 의도적으로 {@code @Transactional}을 붙이지 않는다. {@link
 * com.ootd.pickup.bid.service.BidService#placeBid(Long, Long, PlaceBidRequest, Long)}도
 * {@code @Transactional}이라, 만약 이 메서드까지 트랜잭션으로 감싸면 같은 트랜잭션에 참여하게 되어 {@code placeBid}가 던진 {@link
 * PickUpException}이 그 트랜잭션을 즉시 rollback-only로 마킹한다 — 바깥에서 catch해도 이미 커밋할 수 없는 상태가 되어 {@code
 * BidRequest} 실패 기록조차 저장되지 않는다. {@code placeBid} 호출과 상태 기록을 서로 다른 트랜잭션({@link
 * BidRequestStatusService})으로 분리해야 실패 사유가 실제로 커밋된다.
 *
 * <p>{@code placeBid} 커밋과 {@link BidRequestStatusService#markSucceeded} 커밋 사이에 프로세스가 죽으면 {@code
 * BidRequest}는 {@code PENDING}으로 남고, SQS 재전달로 이 메서드가 다시 호출되어 {@code placeBid}가 다시 시도된다. 이때
 * {@code bid.bid_request_id} 유니크 제약이 두 번째 삽입을 막아 {@link org.springframework.dao.DataIntegrityViolationException}이
 * 던져지는데, 이는 이 메서드에서 잡지 않고 호출자({@code BidRequestCreatedEventHandler})에게 그대로 흘려보낸다 — {@code
 * SettlementEventHandler}가 정산 유니크 제약 충돌을 다루는 것과 동일한 이유로, 트랜잭션이 이미 롤백된 시점(트랜잭션 경계 밖)에서 잡아야 한다.
 */
@Service
@RequiredArgsConstructor
public class BidRequestProcessingService {

  private final BidRequestRepository bidRequestRepository;
  private final BidService bidService;
  private final BidRequestStatusService statusService;

  public void process(BidRequestCreatedMessageQueueEvent event) {
    BidRequest bidRequest =
        bidRequestRepository
            .findById(event.bidRequestId())
            .orElseThrow(
                () -> new IllegalStateException("BidRequest를 찾을 수 없습니다 - id=" + event.bidRequestId()));
    if (bidRequest.getStatus() != BidRequestStatus.PENDING) {
      // SQS는 at-least-once 전달이라 이미 처리된 요청이 재전달될 수 있다.
      return;
    }

    try {
      bidService.placeBid(
          event.auctionId(),
          event.memberId(),
          new PlaceBidRequest(event.bidPrice()),
          event.bidRequestId());
      statusService.markSucceeded(event.bidRequestId());
    } catch (PickUpException exception) {
      statusService.markFailed(event, exception);
    }
  }
}
