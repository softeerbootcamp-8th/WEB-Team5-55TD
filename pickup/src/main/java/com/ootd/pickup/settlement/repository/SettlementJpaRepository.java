package com.ootd.pickup.settlement.repository;

import com.ootd.pickup.settlement.domain.Settlement;
import com.ootd.pickup.settlement.domain.SettlementType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementJpaRepository extends JpaRepository<Settlement, Long> {

  boolean existsByAuctionAuctionIdAndMemberMemberIdAndSettlementType(
      Long auctionId, Long memberId, SettlementType settlementType);
}
