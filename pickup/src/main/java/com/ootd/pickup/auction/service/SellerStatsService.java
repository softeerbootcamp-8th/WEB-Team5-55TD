package com.ootd.pickup.auction.service;

import static com.ootd.pickup.auction.domain.AuctionStatus.ONGOING;
import static com.ootd.pickup.auction.domain.AuctionStatus.SCHEDULED;
import static com.ootd.pickup.auction.domain.AuctionStatus.WON;

import com.ootd.pickup.auction.dto.response.SellerStatsResponse;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerStatsService {

  private final ConsignmentRepository consignmentRepository;
  private final AuctionRepository auctionRepository;

  public SellerStatsResponse getSellerStats(Long memberId) {
    long registeredConsignments = consignmentRepository.countBySellerMemberId(memberId);
    long scheduledAuctions = auctionRepository.countBySellerMemberIdAndStatus(memberId, SCHEDULED);
    long ongoingAuctions = auctionRepository.countBySellerMemberIdAndStatus(memberId, ONGOING);
    long wonConsignments = auctionRepository.countBySellerMemberIdAndStatus(memberId, WON);

    return new SellerStatsResponse(
        registeredConsignments, scheduledAuctions, ongoingAuctions, wonConsignments);
  }
}
