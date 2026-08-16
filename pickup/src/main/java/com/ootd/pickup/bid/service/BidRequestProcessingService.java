package com.ootd.pickup.bid.service;

import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.domain.BidRequestStatus;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.repository.BidRequestRepository;
import com.ootd.pickup.global.exception.PickUpException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * BidRequest}는 {@code PENDING}으로 남고, SQS 재전달로 이 메서드가 다시 호출되어 {@code placeBid}가 다시 시도된다. 이때 {@code
 * bid.bid_request_id} 유니크 제약이 두 번째 삽입을 막아 {@link
 * org.springframework.dao.DataIntegrityViolationException}이 던져지는데, 이는 이 메서드에서 잡지 않고 호출자({@code
 * BidRequestCreatedEventHandler})에게 그대로 흘려보낸다 — {@code SettlementEventHandler}가 정산 유니크 제약 충돌을 다루는
 * 것과 동일한 이유로, 트랜잭션이 이미 롤백된 시점(트랜잭션 경계 밖)에서 잡아야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidRequestProcessingService {

  /** Datadog 등에서 에러를 Critical로 수집하기 위한 마커. */
  private static final Marker CRITICAL_MARKER = MarkerFactory.getMarker("CRITICAL");

  private final BidRequestRepository bidRequestRepository;
  private final BidService bidService;
  private final BidRequestStatusService statusService;

  public void process(BidRequestCreatedMessageQueueEvent event) {
    BidRequest bidRequest =
        bidRequestRepository
            .findById(event.bidRequestId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "BidRequest를 찾을 수 없습니다 - id=" + event.bidRequestId()));
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

  /**
   * 같은 그룹의 배치를 낙관적으로 트랜잭션 하나에 몰아 시도하고, 예기치 못한 실패가 나면 오늘과 같은 방식(건별 독립 트랜잭션)으로 다시 시도한다.
   *
   * <p>대다수 상황(배치 안에 재전달 중복이 없는 경우)엔 커밋이 한 번으로 끝난다. 재전달 중복 같은 드문 경우엔 {@link #placeBidsIndividually}가
   * 오늘과 동일한, 이미 검증된 코드 경로를 그대로 탄다.
   */
  public List<BidRequestCreatedMessageQueueEvent> placeBidsForGroup(
      List<BidRequestCreatedMessageQueueEvent> events) {
    try {
      return placeBidsTogether(events);
    } catch (RuntimeException unexpected) {
      log.warn("배치 처리 중 예기치 못한 실패가 발생해 건별로 다시 시도합니다 - count={}", events.size(), unexpected);
      return placeBidsIndividually(events);
    }
  }

  /**
   * 트랜잭션 하나로 배치 전체를 처리한다.
   *
   * <p>{@link PickUpException}(업무 실패)은 안전하다 — {@code placeBid}는 어떤 저장도 하기 전에 던지므로 이 트랜잭션을
   * rollback-only로 만들지 않는다. 그 외 예외(재전달 중복으로 인한 {@link DataIntegrityViolationException} 등)는 그대로 던져
   * 전체를 롤백시킨다.
   */
  @Transactional
  List<BidRequestCreatedMessageQueueEvent> placeBidsTogether(
      List<BidRequestCreatedMessageQueueEvent> events) {
    List<BidRequestCreatedMessageQueueEvent> done = new ArrayList<>();
    for (BidRequestCreatedMessageQueueEvent event : events) {
      process(event);
      done.add(event);
    }
    return done;
  }

  /**
   * 오늘과 완전히 동일한 방식 — 메시지마다 독립 트랜잭션({@code placeBid} + {@code markSucceeded}/{@code markFailed} 각각).
   */
  private List<BidRequestCreatedMessageQueueEvent> placeBidsIndividually(
      List<BidRequestCreatedMessageQueueEvent> events) {
    List<BidRequestCreatedMessageQueueEvent> done = new ArrayList<>();
    for (BidRequestCreatedMessageQueueEvent event : events) {
      try {
        process(event);
      } catch (DataIntegrityViolationException alreadyProcessed) {
        // 재전달 사이 이미 처리된 요청 - BidRequestCreatedEventHandler.handle()이 오늘 하던 것과 동일
        statusService.markSucceeded(event.bidRequestId());
      } catch (RuntimeException stillUnexpected) {
        log.error(
            CRITICAL_MARKER, "배치 건별 재시도도 실패했습니다 - eventId={}", event.eventId(), stillUnexpected);
        break; // 이후 메시지는 시도하지 않는다 - 순서 보장을 위해 다음 전달 때로 미룬다
      }
      done.add(event);
    }
    return done;
  }
}
