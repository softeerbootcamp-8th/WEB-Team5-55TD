package com.ootd.pickup.bid.service;

import static com.ootd.pickup.auction.domain.AuctionStatus.ONGOING;
import static com.ootd.pickup.auction.domain.AuctionStatus.SCHEDULED;
import static com.ootd.pickup.bid.domain.BidStatus.HIGHEST;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_ENDED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_STARTED;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_SELLER_BID_FORBIDDEN;
import static com.ootd.pickup.global.exception.ExceptionCode.BELOW_MIN_INCREMENT;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.OUTBID_EXISTS;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.dto.response.PlaceBidResponse;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

  private final AuctionRepository auctionRepository;
  private final BidRepository bidRepository;
  private final MemberRepository memberRepository;

  @Transactional
  public PlaceBidResponse placeBid(Long auctionId, Long memberId, PlaceBidRequest request) {
    Auction auction =
        auctionRepository
            .findByIdForUpdate(auctionId)
            .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));

    validateAuction(auction, memberId);

    Member member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new PickUpException(MEMBER_NOT_FOUND));
    Optional<Bid> currentHighestBid =
        bidRepository.findFirstByAuctionIdAndBidStatus(auctionId, HIGHEST);
    Long currentPrice =
        currentHighestBid.map(Bid::getBidPrice).orElseGet(auction::getStartingPrice);

    validateBidPrice(request.bidPrice(), currentPrice, auction.getMinimumBidIncrement());

    currentHighestBid.ifPresent(
        bid -> {
          bid.outbid();
          bidRepository.save(bid);
        });

    Bid savedBid = bidRepository.save(Bid.create(auction, member, request.bidPrice()));
    return PlaceBidResponse.from(savedBid);
  }

  private void validateAuction(Auction auction, Long memberId) {
    if (auction.getAuctionStatus() == SCHEDULED) {
      throw new PickUpException(AUCTION_NOT_STARTED);
    }
    if (auction.getAuctionStatus() != ONGOING
        || (auction.getEndedAt() != null && !auction.getEndedAt().isAfter(LocalDateTime.now()))) {
      throw new PickUpException(AUCTION_ENDED);
    }
    if (auction.getConsignment().getSellerMember().getMemberId().equals(memberId)) {
      throw new PickUpException(AUCTION_SELLER_BID_FORBIDDEN);
    }
  }

  private void validateBidPrice(Long bidPrice, Long currentPrice, Long minimumBidIncrement) {
    if (bidPrice <= currentPrice) {
      throw new PickUpException(OUTBID_EXISTS);
    }
    if (bidPrice - currentPrice < minimumBidIncrement) {
      throw new PickUpException(BELOW_MIN_INCREMENT);
    }
  }
}
