package com.ootd.pickup.auction.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.Watch;
import com.ootd.pickup.auction.dto.response.WatchResponse;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.auction.repository.watch.WatchRepository;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.service.MemberManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchService {

  private final MemberManageService memberManageService;
  private final AuctionRepository auctionRepository;
  private final WatchRepository watchRepository;

  @Transactional
  public WatchResponse registerWatch(Long memberId, Long auctionId) {
    Member member = memberManageService.getMemberById(memberId);
    Auction auction =
        auctionRepository
            .findById(auctionId)
            .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));

    if (auction.getConsignment().getSellerMember().getMemberId().equals(memberId)) {
      throw new PickUpException(AUCTION_SELLER_WATCH_FORBIDDEN);
    }

    try {
      Watch watch = watchRepository.save(Watch.builder().member(member).auction(auction).build());
      watchRepository.flush();
      return WatchResponse.from(watch);
    } catch (DataIntegrityViolationException exception) {
      throw new PickUpException(WATCH_ALREADY_EXISTS);
    }
  }

  @Transactional
  public void deleteWatch(Long memberId, Long auctionId) {
    watchRepository.deleteByMemberIdAndAuctionId(memberId, auctionId);
  }

  @Transactional
  public void deleteWatchesByAuctionId(Long auctionId) {
    watchRepository.deleteByAuctionId(auctionId);
  }
}
