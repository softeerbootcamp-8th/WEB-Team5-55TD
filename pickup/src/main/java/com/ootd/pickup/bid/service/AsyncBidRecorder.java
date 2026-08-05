package com.ootd.pickup.bid.service;

import static com.ootd.pickup.bid.domain.BidStatus.HIGHEST;
import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_FOUND;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_NOT_FOUND;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재가 갱신 트랜잭션이 끝난 뒤 입찰 기록(추월 처리 + Bid 저장)을 비동기로 수행한다. auction row 락과 완전히 분리되어 있어 이 작업이 지연되어도 다른 입찰
 * 요청을 막지 않는다.
 *
 * <p>단일 스레드 실행기({@code bidRecordingExecutor})에서만 처리된다. 이 메서드가 실패해도 응답은 이미 나간 뒤이므로 예외를 삼키고 로그만 남긴다 —
 * 호출자에게 알릴 방법이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncBidRecorder {

  private final AuctionRepository auctionRepository;
  private final BidRepository bidRepository;
  private final MemberRepository memberRepository;

  @Async("bidRecordingExecutor")
  @Transactional
  public void recordBid(Long auctionId, Long memberId, Long bidPrice) {
    try {
      Auction auction =
          auctionRepository
              .findById(auctionId)
              .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));
      Member member =
          memberRepository
              .findById(memberId)
              .orElseThrow(() -> new PickUpException(MEMBER_NOT_FOUND));

      bidRepository
          .findFirstByAuctionIdAndBidStatus(auctionId, HIGHEST)
          .ifPresent(
              bid -> {
                bid.outbid();
                bidRepository.save(bid);
              });

      bidRepository.save(Bid.create(auction, member, bidPrice));
    } catch (Exception exception) {
      log.error(
          "입찰 기록 비동기 처리 실패 - auctionId={}, memberId={}, bidPrice={}",
          auctionId,
          memberId,
          bidPrice,
          exception);
    }
  }
}
