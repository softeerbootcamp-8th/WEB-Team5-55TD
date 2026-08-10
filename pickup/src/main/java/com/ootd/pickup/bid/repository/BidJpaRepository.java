package com.ootd.pickup.bid.repository;

import com.ootd.pickup.bid.domain.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidJpaRepository extends JpaRepository<Bid, Long> {

  boolean existsByMember_MemberIdAndBidStatus(Long memberId, BidStatus bidStatus);
}
