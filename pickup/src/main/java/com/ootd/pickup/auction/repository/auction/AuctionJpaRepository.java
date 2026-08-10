package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionJpaRepository extends JpaRepository<Auction, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select auction
      from Auction auction
      join fetch auction.consignment consignment
      where auction.auctionId = :auctionId
      """)
  Optional<Auction> findByIdForUpdate(@Param("auctionId") Long auctionId);

  @Query(
      "select count(a) from Auction a where a.consignment.sellerMember.memberId = :memberId and a.auctionStatus = :status")
  long countBySellerMemberIdAndStatus(
      @Param("memberId") Long memberId, @Param("status") AuctionStatus status);
}
