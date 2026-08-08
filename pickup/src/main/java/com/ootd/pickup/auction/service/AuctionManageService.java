package com.ootd.pickup.auction.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.global.exception.PickUpException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionManageService {

  private final AuctionRepository auctionRepository;

  public Auction getAuctionById(Long auctionId) {
    return auctionRepository
        .findById(auctionId)
        .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));
  }

  public Map<Long, Long> findAuctionIdsByConsignments(List<Consignment> consignments) {
    return auctionRepository.findAuctionIdsByConsignmentIn(consignments);
  }
}
