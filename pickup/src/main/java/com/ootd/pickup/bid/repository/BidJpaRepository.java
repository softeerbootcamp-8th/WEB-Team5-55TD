package com.ootd.pickup.bid.repository;

import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidJpaRepository extends JpaRepository<Bid, Long> {

  Optional<Bid> findFirstByAuctionAuctionIdAndBidStatusOrderByBidPriceDesc(
      Long auctionId, BidStatus bidStatus);
}
