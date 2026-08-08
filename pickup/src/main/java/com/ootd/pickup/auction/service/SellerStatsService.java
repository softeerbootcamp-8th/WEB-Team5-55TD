package com.ootd.pickup.auction.service;

import static com.ootd.pickup.auction.domain.AuctionStatus.ONGOING;
import static com.ootd.pickup.auction.domain.AuctionStatus.SCHEDULED;
import static com.ootd.pickup.auction.domain.AuctionStatus.WON;

import com.ootd.pickup.auction.dto.response.SellerStatsResponse;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerStatsService {

  private final ConsignmentJpaRepository consignmentJpaRepository;
  private final AuctionJpaRepository auctionJpaRepository;

  public SellerStatsResponse getSellerStats(Long memberId) {
    long registeredConsignments = consignmentJpaRepository.countBySellerMemberId(memberId);
    long scheduledAuctions =
        auctionJpaRepository.countBySellerMemberIdAndStatus(memberId, SCHEDULED);
    long ongoingAuctions =
        auctionJpaRepository.countBySellerMemberIdAndStatus(memberId, ONGOING);
    long wonConsignments =
        auctionJpaRepository.countBySellerMemberIdAndStatus(memberId, WON);

    return new SellerStatsResponse(
        registeredConsignments, scheduledAuctions, ongoingAuctions, wonConsignments);
  }
}
