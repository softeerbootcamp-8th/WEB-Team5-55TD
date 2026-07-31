package com.ootd.pickup.auction.repository.watch;

import com.ootd.pickup.auction.domain.Watch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchJpaRepository extends JpaRepository<Watch, Long> {

  @Modifying
  @Query(
      """
      delete from Watch watch
      where watch.member.memberId = :memberId
        and watch.auction.auctionId = :auctionId
      """)
  int deleteByMemberIdAndAuctionId(
      @Param("memberId") Long memberId, @Param("auctionId") Long auctionId);
}
