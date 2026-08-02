package com.ootd.pickup.auction.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.request.CreateAuctionRequest;
import com.ootd.pickup.auction.dto.request.GetMyAuctionsRequest;
import com.ootd.pickup.auction.dto.request.SearchAuctionsRequest;
import com.ootd.pickup.auction.dto.response.AuctionDetailResponse;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.dto.response.CreateAuctionResponse;
import com.ootd.pickup.auction.repository.auction.AuctionCursor;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.auction.repository.auction.AuctionSort;
import com.ootd.pickup.auction.repository.auction.SalesCursor;
import com.ootd.pickup.auction.repository.watch.WatchRepository;
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
import com.ootd.pickup.global.util.EpochMillis;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
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

  @Transactional
  public CreateAuctionResponse registerAuction(Long memberId, CreateAuctionRequest request) {
    Consignment consignment = getConsignment(request.consignmentId());

    if (!consignment.getSellerMember().getMemberId().equals(memberId)) {
      throw new PickUpException(CONSIGNMENT_AUCTION_OWNER_MISMATCH);
    }

    consignment.scheduleAuction();

    Long bidIncrement = Math.round(request.startingPrice() * BID_INCREMENT_RATIO);
    Auction auction = auctionRepository.save(request.toEntity(consignment, bidIncrement));

    return CreateAuctionResponse.from(auction);
  }

  public CursorPageResponse<AuctionListItemResponse, String> searchAuctions(
      Long viewerMemberId, SearchAuctionsRequest request) {
    AuctionSort sort = AuctionSort.from(request.sort());
    List<AuctionStatus> statuses =
        request.status() == null
            ? List.of()
            : request.status().stream().map(AuctionStatus::from).toList();

    if (request.limit() != null) {
      int limit = validatePositiveLimit(request.limit());
      List<Auction> auctions =
          auctionRepository.searchAuctions(request.q(), statuses, sort, null, limit);
      Assembled assembled = assemble(auctions, viewerMemberId);
      return CursorPageResponse.from(assembled.items(), false, null);
    }

    int size = CursorPageSize.resolve(request.size());
    AuctionCursor decodedCursor = AuctionCursor.decode(request.cursor(), sort);
    List<Auction> fetched =
        auctionRepository.searchAuctions(request.q(), statuses, sort, decodedCursor, size + 1);

    boolean hasNext = fetched.size() > size;
    List<Auction> page = hasNext ? fetched.subList(0, size) : fetched;

    Assembled assembled = assemble(page, viewerMemberId);

    String nextCursor = null;
    if (hasNext) {
      Auction last = page.getLast();
      long lastWatchCount = assembled.watchCounts().getOrDefault(last.getAuctionId(), 0L);
      nextCursor =
          AuctionCursor.encode(
              sort, AuctionCursor.sortValueOf(sort, last, lastWatchCount), last.getAuctionId());
    }

    return CursorPageResponse.from(assembled.items(), hasNext, nextCursor);
  }

  public CursorPageResponse<AuctionListItemResponse, String> getMyOngoingAuctions(
      Long sellerMemberId, GetMyAuctionsRequest request) {
    int size = CursorPageSize.resolve(request.size());
    SalesCursor decodedCursor = SalesCursor.decode(request.cursor());

    List<Auction> fetched =
        auctionRepository.findAllBySellerMemberIdWithCard(
            sellerMemberId, List.of(AuctionStatus.ONGOING), decodedCursor, size + 1);

    boolean hasNext = fetched.size() > size;
    List<Auction> page = hasNext ? fetched.subList(0, size) : fetched;

    Assembled assembled = assemble(page, sellerMemberId);

    String nextCursor = null;
    if (hasNext) {
      Auction last = page.getLast();
      nextCursor = SalesCursor.encode(EpochMillis.from(last.getEndedAt()), last.getAuctionId());
    }

    return CursorPageResponse.from(assembled.items(), hasNext, nextCursor);
  }

  public AuctionDetailResponse getAuctionDetail(Long viewerMemberId, Long auctionId) {
    Auction auction = getAuction(auctionId);
    Consignment consignment = auction.getConsignment();

    Certificate certificate = getCertificate(consignment);
    List<ConsignmentImage> images =
        consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment);

    long watchCount =
        watchRepository.countByAuctionIds(List.of(auctionId)).getOrDefault(auctionId, 0L);
    boolean watched =
        !watchRepository.findWatchedAuctionIds(viewerMemberId, List.of(auctionId)).isEmpty();

    return AuctionDetailResponse.of(auction, certificate, images, watchCount, watched);
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

  private record Assembled(List<AuctionListItemResponse> items, Map<Long, Long> watchCounts) {}

  private Assembled assemble(List<Auction> auctions, Long viewerMemberId) {
    List<Long> auctionIds = auctions.stream().map(Auction::getAuctionId).toList();
    List<Long> consignmentIds =
        auctions.stream().map(a -> a.getConsignment().getConsignmentId()).toList();

    Map<Long, Long> watchCounts = watchRepository.countByAuctionIds(auctionIds);
    Set<Long> watchedIds = watchRepository.findWatchedAuctionIds(viewerMemberId, auctionIds);

    Map<Long, Certificate> certificatesByConsignmentId =
        certificateManageService.getCertificatesByConsignmentId(consignmentIds);

    Map<Long, String> thumbnailsByConsignmentId = resolveThumbnails(consignmentIds);

    List<AuctionListItemResponse> items =
        auctions.stream()
            .map(
                a ->
                    AuctionListItemResponse.of(
                        a,
                        certificatesByConsignmentId.get(a.getConsignment().getConsignmentId()),
                        thumbnailsByConsignmentId.get(a.getConsignment().getConsignmentId()),
                        watchCounts.getOrDefault(a.getAuctionId(), 0L),
                        watchedIds.contains(a.getAuctionId())))
            .toList();

    return new Assembled(items, watchCounts);
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
                ConsignmentImage::getImageUrl,
                (first, second) -> first));
  }

  private int validatePositiveLimit(Integer limit) {
    if (limit < 1) {
      throw new PickUpException(ILLEGAL_ARGUMENT);
    }
    return Math.min(limit, CursorPageSize.MAX_SIZE);
  }
}
