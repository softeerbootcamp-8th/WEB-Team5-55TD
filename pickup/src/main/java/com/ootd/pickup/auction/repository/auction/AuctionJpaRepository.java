package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.Auction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionJpaRepository extends JpaRepository<Auction, Long> {

  @Modifying
  @Query(
      """
      update Auction a
      set a.currentPrice = :newPrice
      where a.auctionId = :auctionId
        and a.currentPrice < :newPrice
        and (:newPrice - a.currentPrice) >= a.bidIncrement
      """)
  int updateCurrentPriceIfHigher(
      @Param("auctionId") Long auctionId, @Param("newPrice") Long newPrice);
}
