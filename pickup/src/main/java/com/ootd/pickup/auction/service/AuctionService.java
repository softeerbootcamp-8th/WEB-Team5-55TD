package com.ootd.pickup.auction.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.request.CreateAuctionRequest;
import com.ootd.pickup.auction.dto.request.SearchAuctionsRequest;
import com.ootd.pickup.auction.dto.response.AuctionDetailResponse;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.dto.response.CreateAuctionResponse;
import com.ootd.pickup.auction.repository.auction.AuctionCursor;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.auction.repository.auction.AuctionSearchField;
import com.ootd.pickup.auction.repository.auction.AuctionSort;
import com.ootd.pickup.auction.repository.watch.WatchRepository;
import com.ootd.pickup.auction.repository.watch.WatchSummary;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.consignments.repository.certificate.CertificateRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import com.ootd.pickup.consignments.repository.consignmentImage.ConsignmentImageRepository;
import com.ootd.pickup.consignments.service.CertificateManageService;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.util.CursorPageSize;
import com.ootd.pickup.images.service.ImageUrlResolver;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {

  private static final double BID_INCREMENT_RATIO = 0.05;

  private final ConsignmentRepository consignmentRepository;
  private final AuctionRepository auctionRepository;
  private final CertificateRepository certificateRepository;
  private final CertificateManageService certificateManageService;
  private final ConsignmentImageRepository consignmentImageRepository;
  private final WatchRepository watchRepository;
  private final ImageUrlResolver imageUrlResolver;
  private final BidRepository bidRepository;

  @Transactional
  public CreateAuctionResponse registerAuction(Long memberId, CreateAuctionRequest request) {
    Consignment consignment = getConsignment(request.consignmentId());

    if (!consignment.getSellerMember().getMemberId().equals(memberId)) {
      throw new PickUpException(CONSIGNMENT_AUCTION_OWNER_MISMATCH);
    }

    validateAuctionPrices(request.startingPrice(), request.reserve());
    Long bidIncrement = calculateBidIncrement(request.startingPrice());
    log.debug(
        "최소 입찰 단위를 계산했습니다 - startingPrice={}, bidIncrement={}",
        request.startingPrice(),
        bidIncrement);

    consignment.scheduleAuction();
    Auction auction = auctionRepository.save(request.toEntity(consignment, bidIncrement));

    log.info(
        "경매를 등록했습니다 - auctionId={}, consignmentId={}, sellerMemberId={}, startingPrice={}, startedAt={}",
        auction.getAuctionId(),
        consignment.getConsignmentId(),
        memberId,
        request.startingPrice(),
        auction.getStartedAt());
    return CreateAuctionResponse.from(auction);
  }

  private void validateAuctionPrices(Long startingPrice, Long reservePrice) {
    if (startingPrice > reservePrice) {
      throw new PickUpException(STARTING_PRICE_EXCEEDS_RESERVE_PRICE);
    }
  }

  /**
   * startingPrice에 업계 기준 상한을 두는 대신, 이후 계산(예: {@code AuctionDetailResponse.nextMinBid()}의
   * currentPrice + bidIncrement)이 Long 범위를 넘지 않는지만 기술적으로 검증한다. 여기서 걸러지지 않고 저장된 뒤에는 addExact가
   * ArithmeticException으로 막아 500 + Slack 알림으로 이어지지만, 그건 "저장되면 안 됐던 값이 저장된" 마지막 방어선이지 사용자에게 보여줄 응답이
   * 아니다. 정상 입력이라면 여기서 400으로 끝나야 한다.
   */
  private Long calculateBidIncrement(Long startingPrice) {
    long bidIncrement = Math.round(startingPrice * BID_INCREMENT_RATIO);
    try {
      Math.addExact(startingPrice, bidIncrement);
    } catch (ArithmeticException e) {
      throw new PickUpException(STARTING_PRICE_TOO_LARGE);
    }
    return bidIncrement;
  }

  public CursorPageResponse<AuctionListItemResponse, String> searchAuctions(
      Long viewerMemberId, SearchAuctionsRequest request) {
    AuctionSort sort = AuctionSort.from(request.sort());
    AuctionSearchField searchField = AuctionSearchField.from(request.searchField());
    List<AuctionStatus> statuses =
        request.status() == null
            ? List.of()
            // 같은 상태를 여러 번 보내도 결과가 달라지지 않도록 중복을 걷어낸다.
            : request.status().stream().map(AuctionStatus::from).distinct().toList();

    if (request.limit() != null) {
      int limit = validatePositiveLimit(request.limit());
      List<Auction> auctions =
          auctionRepository.searchAuctions(
              request.q(),
              searchField,
              statuses,
              sort,
              null,
              limit,
              request.sellerId(),
              request.cardId(),
              request.excludeAuctionId());
      Assembled assembled = assemble(auctions, viewerMemberId);
      return CursorPageResponse.from(assembled.items(), false, null);
    }

    int size = CursorPageSize.resolve(request.size());
    AuctionCursor decodedCursor = AuctionCursor.decode(request.cursor(), sort);
    List<Auction> fetched =
        auctionRepository.searchAuctions(
            request.q(),
            searchField,
            statuses,
            sort,
            decodedCursor,
            size + 1,
            request.sellerId(),
            request.cardId(),
            request.excludeAuctionId());

    boolean hasNext = fetched.size() > size;
    List<Auction> page = hasNext ? fetched.subList(0, size) : fetched;

    Assembled assembled = assemble(page, viewerMemberId);

    String nextCursor = null;
    if (hasNext) {
      Auction last = page.getLast();
      long lastWatchCount =
          assembled.watchSummaries().getOrDefault(last.getAuctionId(), WatchSummary.EMPTY).count();
      nextCursor =
          AuctionCursor.encode(
              sort, AuctionCursor.sortValueOf(sort, last, lastWatchCount), last.getAuctionId());
    }

    return CursorPageResponse.from(assembled.items(), hasNext, nextCursor);
  }

  public AuctionListItemResponse getFeaturedAuction(Long viewerMemberId) {
    List<Auction> candidates =
        auctionRepository.searchAuctions(
            null,
            AuctionSearchField.ALL,
            List.of(AuctionStatus.ONGOING),
            AuctionSort.POPULAR,
            null,
            1,
            null,
            null,
            null);
    Auction featured =
        candidates.stream()
            .findFirst()
            .orElseThrow(() -> new PickUpException(FEATURED_AUCTION_NOT_FOUND));

    return assemble(List.of(featured), viewerMemberId).items().getFirst();
  }

  public AuctionDetailResponse getAuctionDetail(Long viewerMemberId, Long auctionId) {
    Auction auction = getAuction(auctionId);
    Consignment consignment = auction.getConsignment();

    Certificate certificate = getCertificate(consignment);
    List<ConsignmentImage> images =
        consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment);

    WatchSummary watchSummary =
        watchRepository
            .findWatchSummariesByAuctionIds(viewerMemberId, List.of(auctionId))
            .getOrDefault(auctionId, WatchSummary.EMPTY);
    boolean myBidWon = resolveMyBidWon(auction, viewerMemberId);

    return AuctionDetailResponse.of(
        auction,
        certificate,
        images,
        watchSummary.count(),
        watchSummary.watchedByViewer(),
        auction.getCurrentPrice(),
        imageUrlResolver,
        myBidWon);
  }

  /**
   * 조회자 본인이 이 경매의 낙찰자인지 판정한다. 판정 근거는 {@link Bid#getBidStatus()}와 같다 — Auction의 winningBidId가 이 경매의
   * 유일한 낙찰 근거다.
   */
  private boolean resolveMyBidWon(Auction auction, Long viewerMemberId) {
    if (viewerMemberId == null
        || auction.getAuctionStatus() != AuctionStatus.WON
        || auction.getWinningBidId() == null) {
      return false;
    }

    return bidRepository
        .findById(auction.getWinningBidId())
        .map(bid -> bid.getMember().getMemberId().equals(viewerMemberId))
        .orElse(false);
  }

  private Consignment getConsignment(Long consignmentId) {
    return consignmentRepository
        .findConsignmentById(consignmentId)
        .orElseThrow(() -> new PickUpException(CONSIGNMENT_NOT_FOUND));
  }

  private Auction getAuction(Long auctionId) {
    return auctionRepository
        .findByIdWithConsignmentAndCard(auctionId)
        .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));
  }

  private Certificate getCertificate(Consignment consignment) {
    return certificateRepository
        .findCertificateByConsignment(consignment)
        .orElseThrow(() -> new PickUpException(CERTIFICATE_NOT_FOUND));
  }

  private record Assembled(
      List<AuctionListItemResponse> items, Map<Long, WatchSummary> watchSummaries) {}

  private Assembled assemble(List<Auction> auctions, Long viewerMemberId) {
    List<Long> auctionIds = auctions.stream().map(Auction::getAuctionId).toList();
    List<Long> consignmentIds =
        auctions.stream().map(a -> a.getConsignment().getConsignmentId()).toList();

    Map<Long, WatchSummary> watchSummaries =
        watchRepository.findWatchSummariesByAuctionIds(viewerMemberId, auctionIds);

    Map<Long, Certificate> certificatesByConsignmentId =
        certificateManageService.getCertificatesByConsignmentId(consignmentIds);

    Map<Long, String> thumbnailsByConsignmentId = resolveThumbnails(consignmentIds);

    List<AuctionListItemResponse> items =
        auctions.stream()
            .map(
                a -> {
                  WatchSummary watchSummary =
                      watchSummaries.getOrDefault(a.getAuctionId(), WatchSummary.EMPTY);
                  return AuctionListItemResponse.of(
                      a,
                      certificatesByConsignmentId.get(a.getConsignment().getConsignmentId()),
                      thumbnailsByConsignmentId.get(a.getConsignment().getConsignmentId()),
                      watchSummary.count(),
                      watchSummary.watchedByViewer(),
                      a.getCurrentPrice());
                })
            .toList();

    return new Assembled(items, watchSummaries);
  }

  private Map<Long, String> resolveThumbnails(List<Long> consignmentIds) {
    if (consignmentIds.isEmpty()) {
      return Map.of();
    }

    List<ConsignmentImage> images =
        consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
            consignmentIds);

    return images.stream()
        .collect(
            Collectors.toMap(
                image -> image.getConsignment().getConsignmentId(),
                image -> imageUrlResolver.resolve(image.getObjectKey()),
                (first, second) -> first));
  }

  private int validatePositiveLimit(Integer limit) {
    if (limit < 1) {
      throw new PickUpException(ILLEGAL_ARGUMENT);
    }
    return Math.min(limit, CursorPageSize.MAX_SIZE);
  }
}
