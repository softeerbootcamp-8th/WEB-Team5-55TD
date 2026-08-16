package com.ootd.pickup.consignments.repository.consignment;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import java.util.List;
import java.util.Optional;

public interface ConsignmentRepository {
  Consignment save(Consignment consignment);

  Optional<Consignment> findConsignmentById(Long consignmentId);

  Optional<Consignment> findByIdForUpdate(Long consignmentId);

  long countBySellerMemberId(Long sellerMemberId);

  void deleteById(Long consignmentId);

  List<Consignment> findAllBySellerMemberIdAndStatusAndLatestAuctionStatusAndCursor(
      Long sellerMemberId,
      ConsignmentStatus status,
      AuctionStatus latestAuctionStatus,
      Long cursor,
      int size);

  boolean existsBySellerMemberIdAndStatus(Long sellerMemberId, ConsignmentStatus consignmentStatus);

  int updateStatusToSoldByAuctionIdIn(List<Long> auctionIds);

  int updateStatusToRegisterableByAuctionIdIn(List<Long> auctionIds);
}
