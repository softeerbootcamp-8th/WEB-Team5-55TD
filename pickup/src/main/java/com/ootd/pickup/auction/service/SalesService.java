package com.ootd.pickup.auction.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.request.GetSalesHistoryRequest;
import com.ootd.pickup.auction.dto.response.SaleHistoryItemResponse;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.auction.repository.auction.SalesCursorPaginator;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.service.CertificateManageService;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;
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

    return SalesCursorPaginator.paginate(
        auctionRepository,
        sellerMemberId,
        statuses,
        request.cursor(),
        request.size(),
        this::assembleItems);
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
