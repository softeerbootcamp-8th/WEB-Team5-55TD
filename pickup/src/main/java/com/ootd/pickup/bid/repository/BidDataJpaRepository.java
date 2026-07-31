package com.ootd.pickup.bid.repository;

import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BidDataJpaRepository implements BidRepository {

  private final BidJpaRepository bidJpaRepository;

  @Override
  public Bid save(Bid bid) {
    return bidJpaRepository.save(bid);
  }

  @Override
  public Optional<Bid> findFirstByAuctionIdAndBidStatus(Long auctionId, BidStatus bidStatus) {
    return bidJpaRepository.findFirstByAuctionAuctionIdAndBidStatusOrderByBidPriceDesc(
        auctionId, bidStatus);
  }
}
