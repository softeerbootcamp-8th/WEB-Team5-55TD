package com.ootd.pickup.auction.service;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.response.SellerStatsResponse;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.consignments.service.ConsignmentManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerStatsService {

  private final AuctionRepository auctionRepository;
  private final ConsignmentManageService consignmentManageService;

  public SellerStatsResponse getMyStats(Long sellerMemberId) {
    long registeredConsignments =
        consignmentManageService.countRegisteredConsignments(sellerMemberId);
    long scheduledAuctions =
        auctionRepository.countBySellerMemberIdAndStatus(sellerMemberId, AuctionStatus.SCHEDULED);
    long ongoingAuctions =
        auctionRepository.countBySellerMemberIdAndStatus(sellerMemberId, AuctionStatus.ONGOING);
    long wonConsignments = consignmentManageService.countWonConsignments(sellerMemberId);

    return new SellerStatsResponse(
        registeredConsignments, scheduledAuctions, ongoingAuctions, wonConsignments);
  }
}
