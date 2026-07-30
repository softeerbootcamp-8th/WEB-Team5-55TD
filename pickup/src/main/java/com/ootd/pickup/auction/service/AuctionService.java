package com.ootd.pickup.auction.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.dto.request.CreateAuctionRequest;
import com.ootd.pickup.auction.dto.response.CreateAuctionResponse;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import com.ootd.pickup.global.exception.PickUpException;
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

  private Consignment getConsignment(Long consignmentId) {
    return consignmentRepository
        .findConsignmentById(consignmentId)
        .orElseThrow(() -> new PickUpException(CONSIGNMENT_NOT_FOUND));
  }
}
