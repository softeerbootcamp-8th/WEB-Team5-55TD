package com.ootd.pickup.bid.service;

import static com.ootd.pickup.auction.domain.AuctionStatus.ONGOING;
import static com.ootd.pickup.auction.domain.AuctionStatus.SCHEDULED;
import static com.ootd.pickup.bid.domain.BidStatus.HIGHEST;
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
import com.ootd.pickup.auction.event.AuctionBidUpdatedNotificationEvent;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
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
    Auction auction =
        auctionRepository
            .findByIdForUpdate(auctionId)
            .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));

    validateAuction(auction, memberId);

    Member member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new PickUpException(MEMBER_NOT_FOUND));
    Optional<Bid> currentHighestBid =
        bidRepository.findFirstByAuctionIdAndBidStatus(auctionId, HIGHEST);
    Long currentPrice =
        currentHighestBid.map(Bid::getBidPrice).orElseGet(auction::getStartingPrice);

    validateBidPrice(request.bidPrice(), currentPrice, auction.getBidIncrement());
    pointReservationService.validateAvailable(auction, member, request.bidPrice());
    Bid savedBid = bidRepository.save(Bid.create(auction, member, request.bidPrice()));
    pointReservationService.reserveHighestBid(auction, savedBid, member);

    currentHighestBid.ifPresent(
        bid -> {
          bid.outbid();
          bidRepository.save(bid);
        });

    auction.updateWinningBid(savedBid.getBidId(), savedBid.getBidPrice());
    auctionRepository.save(auction);
    applicationEventPublisher.publishEvent(
        AuctionBidUpdatedNotificationEvent.fromEntity(auction, savedBid));

    return PlaceBidResponse.from(savedBid);
  }

  private void validateAuction(Auction auction, Long memberId) {
    if (auction.getAuctionStatus() == SCHEDULED) {
      throw new PickUpException(AUCTION_NOT_STARTED);
    }
    if (auction.getAuctionStatus() != ONGOING
        || (auction.getEndedAt() != null && !auction.getEndedAt().isAfter(LocalDateTime.now()))) {
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
