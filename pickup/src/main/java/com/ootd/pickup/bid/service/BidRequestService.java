package com.ootd.pickup.bid.service;

import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_ENDED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_STARTED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_SELLER_BID_FORBIDDEN;
import static com.ootd.pickup.global.exception.ExceptionCode.BELOW_MIN_INCREMENT;
import static com.ootd.pickup.global.exception.ExceptionCode.BID_REQUEST_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.INSUFFICIENT_BID_LIMIT;
import static com.ootd.pickup.global.exception.ExceptionCode.OUTBID_EXISTS;
import static com.ootd.pickup.global.exception.ExceptionCode.POINT_NOT_FOUND;

import com.ootd.pickup.auction.cache.AuctionSnapshot;
import com.ootd.pickup.auction.cache.AuctionSnapshotCache;
import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.dto.response.BidRequestResultResponse;
import com.ootd.pickup.bid.dto.response.CreateBidRequestResponse;
import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.repository.BidRequestRepository;
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.observability.TraceContextCarrier;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입찰 요청 접수. 될 가능성이 없는 요청을 걸러내되, 락을 두고 경합하는 인증(authoritative) 검증은 여기서 하지 않는다.
 *
 * <p>경매 현재가·상태·판매자 본인 여부는 {@link AuctionSnapshotCache}의 스냅샷으로, 포인트 잔액은 락 없는 단순 조회로 미리 거른다. 두 검증 모두
 * 밑져야 본전인 사전 필터일 뿐이다 — 캐시가 실제보다 뒤처져 있어도 "너무 관대하게 통과"할 뿐 "정상 입찰을 잘못 거절"하지 않으며, 최종 검증은 여전히 {@link
 * BidRequestProcessingService}가 {@code Auction} 행 락을 잡고 수행한다.
 */
@Service
@RequiredArgsConstructor
public class BidRequestService {

  private final AuctionRepository auctionRepository;
  private final BidRequestRepository bidRequestRepository;
  private final EventProducer eventProducer;
  private final AuctionSnapshotCache auctionSnapshotCache;
  private final PointRepository pointRepository;

  /**
   * {@code traceParent}에 {@code null}을 넘겨 진짜 원점으로 스팬을 연다.
   *
   * <p>Spring MVC가 만드는 {@code servlet.request} 네이티브 스팬은 {@link TraceContextCarrier}가 쓰는
   * OpenTelemetry API의 활성 컨텍스트로 보이지 않는다(실측 확인됨) — 그래서 여기서 명시적으로 새 스팬을 열지 않으면 {@link
   * com.ootd.pickup.global.event.messagequeue.outbox.OutboxEventEntity}가 뜨려는 트레이스가 항상 비어 있어,
   * Outbox→SQS→ 컨슈머→Redis로 이어지는 비동기 체인 전체가 시작부터 끊긴다.
   */
  @Transactional
  public CreateBidRequestResponse createBidRequest(Long auctionId, Long memberId, Long bidPrice) {
    return TraceContextCarrier.callWithExtractedContext(
        null,
        "BidRequestService.createBidRequest",
        () -> doCreateBidRequest(auctionId, memberId, bidPrice));
  }

  private CreateBidRequestResponse doCreateBidRequest(
      Long auctionId, Long memberId, Long bidPrice) {
    AuctionSnapshot snapshot =
        auctionSnapshotCache.find(auctionId).orElseGet(() -> loadAndCacheSnapshot(auctionId));

    validateSnapshot(snapshot, memberId, bidPrice);
    validatePointBalance(memberId, bidPrice);

    BidRequest bidRequest =
        bidRequestRepository.save(BidRequest.create(auctionId, memberId, bidPrice));
    eventProducer.produce(BidRequestCreatedMessageQueueEvent.fromEntity(bidRequest));

    return CreateBidRequestResponse.from(bidRequest);
  }

  private AuctionSnapshot loadAndCacheSnapshot(Long auctionId) {
    Auction auction =
        auctionRepository
            .findById(auctionId)
            .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));
    AuctionSnapshot snapshot = AuctionSnapshot.fromEntity(auction);
    auctionSnapshotCache.put(snapshot);
    return snapshot;
  }

  private void validateSnapshot(AuctionSnapshot snapshot, Long memberId, Long bidPrice) {
    if (snapshot.auctionStatus() == AuctionStatus.SCHEDULED) {
      throw new PickUpException(AUCTION_NOT_STARTED);
    }
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    if (snapshot.auctionStatus() != AuctionStatus.ONGOING
        || (snapshot.endedAt() != null && !snapshot.endedAt().isAfter(now))) {
      throw new PickUpException(AUCTION_ENDED);
    }
    if (snapshot.sellerMemberId().equals(memberId)) {
      throw new PickUpException(AUCTION_SELLER_BID_FORBIDDEN);
    }
    if (bidPrice <= snapshot.currentPrice()) {
      throw new PickUpException(OUTBID_EXISTS);
    }
    if (bidPrice - snapshot.currentPrice() < snapshot.bidIncrement()) {
      throw new PickUpException(BELOW_MIN_INCREMENT);
    }
  }

  private void validatePointBalance(Long memberId, Long bidPrice) {
    Point point =
        pointRepository
            .findByMemberId(memberId)
            .orElseThrow(() -> new PickUpException(POINT_NOT_FOUND));
    if (bidPrice > point.getAvailableBalance()) {
      throw new PickUpException(INSUFFICIENT_BID_LIMIT);
    }
  }

  @Transactional(readOnly = true)
  public BidRequestResultResponse getBidRequestResult(
      Long auctionId, Long bidRequestId, Long memberId) {
    BidRequest bidRequest =
        bidRequestRepository
            .findById(bidRequestId)
            .filter(
                request ->
                    request.getAuctionId().equals(auctionId)
                        && request.getMemberId().equals(memberId))
            .orElseThrow(() -> new PickUpException(BID_REQUEST_NOT_FOUND));
    return BidRequestResultResponse.from(bidRequest);
  }
}
