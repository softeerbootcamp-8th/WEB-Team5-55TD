package com.ootd.pickup.settlement.repository;

import com.ootd.pickup.settlement.domain.Settlement;
import com.ootd.pickup.settlement.domain.SettlementType;

public interface SettlementRepository {

  boolean existsByAuctionIdAndMemberIdAndSettlementType(
      Long auctionId, Long memberId, SettlementType settlementType);

  Settlement save(Settlement settlement);
}
