package com.ootd.pickup.settlement.repository;

import com.ootd.pickup.settlement.domain.Settlement;
import com.ootd.pickup.settlement.domain.SettlementType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SettlementDataJpaRepository implements SettlementRepository {

  private final SettlementJpaRepository settlementJpaRepository;

  @Override
  public boolean existsByAuctionIdAndMemberIdAndSettlementType(
      Long auctionId, Long memberId, SettlementType settlementType) {
    return settlementJpaRepository.existsByAuctionAuctionIdAndMemberMemberIdAndSettlementType(
        auctionId, memberId, settlementType);
  }

  @Override
  public Settlement save(Settlement settlement) {
    return settlementJpaRepository.save(settlement);
  }
}
