package com.ootd.pickup.auction.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.request.GetSalesHistoryRequest;
import com.ootd.pickup.auction.dto.response.SaleHistoryItemResponse;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.auction.repository.auction.SalesCursor;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.service.CertificateManageService;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.global.util.CursorPageSize;
import com.ootd.pickup.global.util.EpochMillis;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesService {

  private final AuctionRepository auctionRepository;
  private final CertificateManageService certificateManageService;

  public CursorPageResponse<SaleHistoryItemResponse, String> getSalesHistory(
      Long sellerMemberId, GetSalesHistoryRequest request) {
    List<AuctionStatus> statuses = resolveStatuses(request.status());
    int size = CursorPageSize.resolve(request.size());
    SalesCursor decodedCursor = SalesCursor.decode(request.cursor());

    List<Auction> fetched =
        auctionRepository.findAllBySellerMemberIdWithCard(
            sellerMemberId, statuses, decodedCursor, size + 1);

    boolean hasNext = fetched.size() > size;
    List<Auction> page = hasNext ? fetched.subList(0, size) : fetched;

    List<SaleHistoryItemResponse> items = assembleItems(page);

    String nextCursor = null;
    if (hasNext) {
      Auction last = page.getLast();
      nextCursor = SalesCursor.encode(EpochMillis.from(last.getEndedAt()), last.getAuctionId());
    }

    return CursorPageResponse.from(items, hasNext, nextCursor);
  }

  private List<SaleHistoryItemResponse> assembleItems(List<Auction> auctions) {
    List<Long> consignmentIds =
        auctions.stream().map(a -> a.getConsignment().getConsignmentId()).toList();

    Map<Long, Certificate> certificatesByConsignmentId =
        certificateManageService.getCertificatesByConsignmentId(consignmentIds);

    return auctions.stream()
        .map(
            a ->
                SaleHistoryItemResponse.of(
                    a, certificatesByConsignmentId.get(a.getConsignment().getConsignmentId())))
        .toList();
  }

  private List<AuctionStatus> resolveStatuses(String status) {
    if (status == null || status.isBlank()) {
      return AuctionStatus.terminalStatuses();
    }

    AuctionStatus parsed = AuctionStatus.from(status);
    if (!parsed.isTerminal()) {
      throw new PickUpException(INVALID_AUCTION_STATUS);
    }
    return List.of(parsed);
  }
}
