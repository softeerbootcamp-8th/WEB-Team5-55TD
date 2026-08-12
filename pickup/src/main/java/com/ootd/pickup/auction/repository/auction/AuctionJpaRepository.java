package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionJpaRepository extends JpaRepository<Auction, Long> {

  @Modifying(flushAutomatically = true)
  @Query(
      """
      update Auction auction
      set auction.endedAt = :extendedEndAt
      where auction.auctionId = :auctionId
        and auction.auctionStatus = :status
        and auction.endedAt = :currentEndAt
        and auction.endedAt > :bidAt
        and auction.endedAt < :softCloseBoundary
      """)
  int extendEndAtIfClosingSoon(
      @Param("auctionId") Long auctionId,
      @Param("status") AuctionStatus status,
      @Param("currentEndAt") LocalDateTime currentEndAt,
      @Param("bidAt") LocalDateTime bidAt,
      @Param("softCloseBoundary") LocalDateTime softCloseBoundary,
      @Param("extendedEndAt") LocalDateTime extendedEndAt);
}
