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
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.dto.request.GetAuctionBidsRequest;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.dto.response.AuctionBidListItemResponse;
import com.ootd.pickup.bid.dto.response.PlaceBidAcceptedResponse;
import com.ootd.pickup.bid.dto.response.PlaceBidResponse;
import com.ootd.pickup.bid.repository.BidPriceCacheRepository;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.lock.DistributedLock;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
  private final BidPriceCacheRepository bidPriceCacheRepository;
  private final AsyncBidRecorder asyncBidRecorder;

  /** OOTD-278: Redisson 분산 락 + Bid 테이블 기반 현재가 조회로 동시성을 제어한다. */
  @DistributedLock(key = "'auction:' + #auctionId")
  @Transactional
  public PlaceBidResponse placeBidWithDistributedLock(
      Long auctionId, Long memberId, PlaceBidRequest request) {
    Auction auction =
        auctionRepository
            .findById(auctionId)
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

    currentHighestBid.ifPresent(
        bid -> {
          bid.outbid();
          bidRepository.save(bid);
        });

    Bid savedBid = bidRepository.save(Bid.create(auction, member, request.bidPrice()));

    auction.updateWinningBid(savedBid.getBidId(), savedBid.getBidPrice());
    auctionRepository.save(auction);

    return PlaceBidResponse.from(savedBid);
  }

  /** OOTD-279: 조건부 UPDATE(WHERE절)의 영향 row 수만으로 동시성을 제어한다. 캐시는 사용하지 않는다. */
  @Transactional
  public PlaceBidResponse placeBidWithConditionalUpdate(
      Long auctionId, Long memberId, PlaceBidRequest request) {
    Auction auction =
        auctionRepository
            .findById(auctionId)
            .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));

    validateAuction(auction, memberId);
    validateBidPrice(request.bidPrice(), auction.getCurrentPrice(), auction.getBidIncrement());

    Member member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new PickUpException(MEMBER_NOT_FOUND));

    int updated = auctionRepository.updateCurrentPriceIfHigher(auctionId, request.bidPrice());
    if (updated == 0) {
      // 같은 트랜잭션에서 Auction을 다시 조회해도 1차 캐시(영속성 컨텍스트)에 걸려 방금 읽은 stale한
      // 값을 그대로 돌려주므로(벌크 UPDATE는 이를 갈아엎지 않음), 실패 사유를 다시 판별하지 않고
      // 그대로 추월 처리한다. 최종 정합성은 위 UPDATE의 영향 row 수가 이미 보장했다.
      throw new PickUpException(OUTBID_EXISTS);
    }

    bidRepository
        .findFirstByAuctionIdAndBidStatus(auctionId, HIGHEST)
        .ifPresent(
            bid -> {
              bid.outbid();
              bidRepository.save(bid);
            });

    Bid savedBid = bidRepository.save(Bid.create(auction, member, request.bidPrice()));
    return PlaceBidResponse.from(savedBid);
  }

  /** OOTD-292: OOTD-279의 조건부 UPDATE에 Redis 현재가 캐시 사전 검사를 더해 DB 커넥션 소모를 줄인다. */
  @Transactional
  public PlaceBidResponse placeBid(Long auctionId, Long memberId, PlaceBidRequest request) {
    // DB 커넥션을 잡기 전에 캐시로 먼저 거른다. 캐시가 없거나(미스) 통과해도 최종 판정은
    // 아래 updateCurrentPriceIfHigher가 그대로 수행하므로, 캐시가 오래되어도 정합성은 깨지지 않는다.
    bidPriceCacheRepository
        .findCurrentPrice(auctionId)
        .ifPresent(cachedCurrentPrice -> rejectIfNotHigher(request.bidPrice(), cachedCurrentPrice));

    Auction auction =
        auctionRepository
            .findById(auctionId)
            .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));

    validateAuction(auction, memberId);
    validateBidPrice(request.bidPrice(), auction.getCurrentPrice(), auction.getBidIncrement());

    Member member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new PickUpException(MEMBER_NOT_FOUND));

    int updated = auctionRepository.updateCurrentPriceIfHigher(auctionId, request.bidPrice());
    if (updated == 0) {
      Auction latest =
          auctionRepository
              .findById(auctionId)
              .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));
      bidPriceCacheRepository.saveCurrentPrice(auctionId, latest.getCurrentPrice());
      validateBidPrice(request.bidPrice(), latest.getCurrentPrice(), latest.getBidIncrement());
      throw new PickUpException(OUTBID_EXISTS);
    }

    bidPriceCacheRepository.saveCurrentPrice(auctionId, request.bidPrice());

    bidRepository
        .findFirstByAuctionIdAndBidStatus(auctionId, HIGHEST)
        .ifPresent(
            bid -> {
              bid.outbid();
              bidRepository.save(bid);
            });

    Bid savedBid = bidRepository.save(Bid.create(auction, member, request.bidPrice()));
    return PlaceBidResponse.from(savedBid);
  }

  /**
   * OOTD-292 개선안: 트랜잭션을 auction.currentPrice 갱신 한 줄로 최소화한다.
   *
   * <p>이 메서드 자체는 트랜잭션을 열지 않는다({@code Propagation.NOT_SUPPORTED}로 클래스 레벨의 readOnly 트랜잭션을 명시적으로
   * 걷어낸다). 그래서 각 리포지토리 호출은 Spring Data JPA가 자동으로 부여하는 자신만의 짧은 트랜잭션 안에서 실행되고, 커밋 즉시 auction row 락이
   * 풀린다.
   *
   * <ul>
   *   <li>Redis 사전 검사는 DB 트랜잭션을 열기 전에 수행한다 — Redis 통신 문제가 생겨도 DB 커넥션을 붙잡고 있지 않는다.
   *   <li>Redis 캐시 갱신은 현재가 갱신 트랜잭션이 끝난 뒤 동기로 수행한다. Redis 쓰기는 충분히 빨라 응답 지연은 미미하고, 이미 DB 커넥션을 반납한 뒤라
   *       커넥션 점유 문제와는 무관하다. 비동기로 돌리면 지연은 더 줄지만 갱신 순서가 흐트러질 수 있어 동기를 선택했다.
   *   <li>Bid 기록(추월 처리 + 저장)은 응답과 무관하게 완전히 비동기로 수행한다 — auction row 락과 전혀 겹치지 않아 이 작업이 늦어져도 다른 입찰을
   *       막지 않는다.
   * </ul>
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PlaceBidAcceptedResponse placeBidWithShortTransaction(
      Long auctionId, Long memberId, PlaceBidRequest request) {
    // DB 커넥션을 잡기 전에 캐시로 먼저 거른다 — 조회조차 하지 않아야 커넥션 풀 보호 효과가 있다.
    bidPriceCacheRepository
        .findCurrentPrice(auctionId)
        .ifPresent(cachedCurrentPrice -> rejectIfNotHigher(request.bidPrice(), cachedCurrentPrice));

    Auction auction =
        auctionRepository
            .findByIdWithConsignmentAndCard(auctionId)
            .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));

    validateAuction(auction, memberId);
    validateBidPrice(request.bidPrice(), auction.getCurrentPrice(), auction.getBidIncrement());

    int updated = auctionRepository.updateCurrentPriceIfHigher(auctionId, request.bidPrice());
    if (updated == 0) {
      throw new PickUpException(OUTBID_EXISTS);
    }

    bidPriceCacheRepository.saveCurrentPrice(auctionId, request.bidPrice());

    asyncBidRecorder.recordBid(auctionId, memberId, request.bidPrice());

    return new PlaceBidAcceptedResponse(
        auctionId, memberId, request.bidPrice(), LocalDateTime.now());
  }

  private void rejectIfNotHigher(Long bidPrice, Long cachedCurrentPrice) {
    if (bidPrice <= cachedCurrentPrice) {
      throw new PickUpException(OUTBID_EXISTS);
    }
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
