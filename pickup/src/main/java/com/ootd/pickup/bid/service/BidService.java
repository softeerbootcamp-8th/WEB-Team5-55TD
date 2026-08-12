package com.ootd.pickup.bid.service;

import static com.ootd.pickup.auction.domain.AuctionStatus.ONGOING;
import static com.ootd.pickup.auction.domain.AuctionStatus.SCHEDULED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_ENDED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_STARTED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_SELLER_BID_FORBIDDEN;
import static com.ootd.pickup.global.exception.ExceptionCode.BELOW_MIN_INCREMENT;
import static com.ootd.pickup.global.exception.ExceptionCode.ILLEGAL_ARGUMENT;
import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_CURSOR;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.OUTBID_EXISTS;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.event.BidRequestSucceededNotificationEvent;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.dto.request.GetAuctionBidsRequest;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.dto.response.AuctionBidListItemResponse;
import com.ootd.pickup.bid.dto.response.PlaceBidResponse;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.service.PointReservationService;
import com.ootd.pickup.point.service.PointReservationService.PreparedBidReservation;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 100;

  private final AuctionRepository auctionRepository;
  private final BidRepository bidRepository;
  private final MemberRepository memberRepository;
  private final PointReservationService pointReservationService;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Transactional
  public PlaceBidResponse placeBid(Long auctionId, Long memberId, PlaceBidRequest request) {
    return placeBid(auctionId, memberId, request, null);
  }

  @Transactional
  public PlaceBidResponse placeBid(
      Long auctionId, Long memberId, PlaceBidRequest request, Long bidRequestId) {
    Auction auction =
        auctionRepository
            .findByIdForUpdate(auctionId)
            .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));

    LocalDateTime bidAt = LocalDateTime.now(ZoneOffset.UTC);
    validateAuction(auction, memberId, bidAt);

    Member member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new PickUpException(MEMBER_NOT_FOUND));
    // validateAuction이 이미 SCHEDULED를 걸러냈으므로 이 시점의 auction은 항상 ONGOING이라 null이 아니다.
    Long currentPrice = auction.getCurrentPrice();

    log.debug(
        "입찰가 검증 시작 - auctionId={}, requestedBidPrice={}, currentPrice={}, bidIncrement={}",
        auctionId,
        request.bidPrice(),
        currentPrice,
        auction.getBidIncrement());
    validateBidPrice(request.bidPrice(), currentPrice, auction.getBidIncrement());
    PreparedBidReservation preparedReservation =
        pointReservationService.prepareReservation(auction, member, request.bidPrice());
    Bid savedBid =
        bidRepository.save(Bid.create(auction, member, request.bidPrice(), bidRequestId));
    pointReservationService.reserveHighestBid(auction, preparedReservation, savedBid, member);

    Long previousHighestBidId = auction.getWinningBidId();
    auction.updateWinningBid(savedBid.getBidId(), savedBid.getBidPrice());
    if (auctionRepository.extendEndAtIfClosingSoon(auction, bidAt)) {
      log.info("마감 임박 입찰로 경매를 연장했습니다 - auctionId={}, endedAt={}", auctionId, auction.getEndedAt());
    }
    auctionRepository.save(auction);
    applicationEventPublisher.publishEvent(
        BidRequestSucceededNotificationEvent.fromEntity(auction, savedBid, bidRequestId));

    log.info(
        "입찰이 접수됐습니다 - auctionId={}, bidId={}, memberId={}, bidPrice={}, previousHighestBidId={}",
        auctionId,
        savedBid.getBidId(),
        memberId,
        savedBid.getBidPrice(),
        previousHighestBidId);

    return PlaceBidResponse.from(savedBid);
  }

  private void validateAuction(Auction auction, Long memberId, LocalDateTime bidAt) {
    if (auction.getAuctionStatus() == SCHEDULED) {
      throw new PickUpException(AUCTION_NOT_STARTED);
    }
    if (auction.getAuctionStatus() != ONGOING
        || (auction.getEndedAt() != null && !auction.getEndedAt().isAfter(bidAt))) {
      throw new PickUpException(AUCTION_ENDED);
    }
    if (auction.getConsignment().getSellerMember().getMemberId().equals(memberId)) {
      throw new PickUpException(AUCTION_SELLER_BID_FORBIDDEN);
    }
  }

  private void validateBidPrice(Long bidPrice, Long currentPrice, Long bidIncrement) {
    if (bidPrice <= currentPrice) {
      throw new PickUpException(OUTBID_EXISTS);
    }
    if (bidPrice - currentPrice < bidIncrement) {
      throw new PickUpException(BELOW_MIN_INCREMENT);
    }
  }

  public CursorPageResponse<AuctionBidListItemResponse, String> getAuctionBids(
      Long auctionId, Long viewerMemberId, GetAuctionBidsRequest request) {
    auctionRepository.findById(auctionId).orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));

    int size = resolveSize(request.size());
    Long cursorBidId = decodeCursor(request.cursor());

    List<Bid> fetched = bidRepository.findAllByAuctionId(auctionId, cursorBidId, size + 1);
    boolean hasNext = fetched.size() > size;
    List<Bid> page = hasNext ? fetched.subList(0, size) : fetched;

    List<AuctionBidListItemResponse> items =
        page.stream().map(bid -> AuctionBidListItemResponse.of(bid, viewerMemberId)).toList();

    String nextCursor = hasNext ? String.valueOf(page.getLast().getBidId()) : null;
    return CursorPageResponse.from(items, hasNext, nextCursor);
  }

  /** 최고 입찰자인 상태로 탈퇴하면 경매가 종료돼도 낙찰자에게 연락할 수 없게 되므로 탈퇴를 막는다. */
  public boolean hasActiveBid(Long memberId) {
    return bidRepository.existsCurrentHighestBidByMemberId(memberId);
  }

  private Long decodeCursor(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }
    try {
      return Long.parseLong(cursor);
    } catch (NumberFormatException e) {
      throw new PickUpException(INVALID_CURSOR);
    }
  }

  private int resolveSize(Integer size) {
    if (size == null) {
      return DEFAULT_SIZE;
    }
    if (size < 1) {
      throw new PickUpException(ILLEGAL_ARGUMENT);
    }
    return Math.min(size, MAX_SIZE);
  }
}
