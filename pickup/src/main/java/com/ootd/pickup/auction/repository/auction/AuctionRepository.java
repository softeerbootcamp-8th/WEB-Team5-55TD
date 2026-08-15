package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.consignments.domain.Consignment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Limit;

public interface AuctionRepository {
  Auction save(Auction auction);

  Optional<Auction> findById(Long auctionId);

  Optional<Auction> findByIdForUpdate(Long auctionId);

  boolean updateWinningBidAndExtendEndAtIfClosingSoon(
      Auction targetAuction, Long newWinningBidId, Long newWinningPrice, LocalDateTime bidAt);

  int incrementWatchCountById(Long auctionId);

  int decrementWatchCountById(Long auctionId);

  int resetWatchCountById(Long auctionId);

  long countBySellerMemberIdAndStatus(Long sellerMemberId, AuctionStatus status);

  List<Auction> searchAuctions(
      String q,
      AuctionSearchField searchField,
      List<AuctionStatus> statuses,
      AuctionSort sort,
      AuctionCursor cursor,
      int limit,
      Long sellerId,
      Long cardId,
      Long excludeAuctionId);

  Optional<Auction> findByIdWithConsignmentAndCard(Long auctionId);

  List<Auction> findAllBySellerMemberIdWithCard(
      Long sellerMemberId, List<AuctionStatus> statuses, SalesCursor cursor, int limit);

  Map<Long, AuctionSummary> findAuctionSummariesByConsignmentIn(List<Consignment> consignments);

  List<Long> findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow(
      AuctionStatus auctionStatus, Limit limit);

  List<Long> findAllIdsByAuctionStatusAndEndedAtLessThanEqualNow(
      AuctionStatus auctionStatus, Limit limit);

  int updateAuctionStatusToOngoingByIdIn(List<Long> auctionIds);

  int updateAuctionStatusToWonByIdIn(List<Long> auctionIds);

  int updateAuctionStatusToPassedByIdIn(List<Long> auctionIds);

  List<Auction> findAllWithConsignmentAndSellerMemberByIdIn(List<Long> auctionIds);

  List<Bid> findAllBidsWithMemberByIdIn(List<Long> bidIds);
}
