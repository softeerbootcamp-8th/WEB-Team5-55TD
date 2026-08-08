package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.consignments.domain.Consignment;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AuctionRepository {
  Auction save(Auction auction);

  Optional<Auction> findById(Long auctionId);

  Optional<Auction> findByIdForUpdate(Long auctionId);

  List<Auction> searchAuctions(
      String q, List<AuctionStatus> statuses, AuctionSort sort, AuctionCursor cursor, int limit);

  Optional<Auction> findByIdWithConsignmentAndCard(Long auctionId);

  List<Auction> findAllBySellerMemberIdWithCard(
      Long sellerMemberId, List<AuctionStatus> statuses, SalesCursor cursor, int limit);

  Map<Long, Long> findAuctionIdsByConsignmentIn(List<Consignment> consignments);
}
