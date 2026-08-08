package com.ootd.pickup.bid.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.repository.BidJpaRepository;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointJpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BidConcurrencyTest {

  @Autowired private BidService bidService;

  @Autowired private BidJpaRepository bidJpaRepository;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private PointJpaRepository pointJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @AfterEach
  void tearDown() {
    bidJpaRepository.deleteAll();
    auctionJpaRepository.deleteAll();
    consignmentJpaRepository.deleteAll();
    cardJpaRepository.deleteAll();
    pointJpaRepository.deleteAll();
    memberJpaRepository.deleteAll();
  }

  @Test
  void 동시에_입찰해도_가장_높은_입찰만_최고상태로_남는다() throws Exception {
    // given
    Member seller = memberJpaRepository.save(Member.create("seller", "password", "판매자"));
    Member firstBidder = memberJpaRepository.save(Member.create("bidder1", "password", "입찰자1"));
    Member secondBidder = memberJpaRepository.save(Member.create("bidder2", "password", "입찰자2"));
    pointJpaRepository.save(createPointWithBalance(firstBidder.getMemberId(), 50_000L));
    pointJpaRepository.save(createPointWithBalance(secondBidder.getMemberId(), 50_000L));
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName("테스트 카드")
                .cardNumber("001")
                .setName("테스트 세트")
                .language(Language.KOREAN)
                .rarity(Rarity.MINT)
                .imageUrl("https://example.com/card.png")
                .build());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(seller)
                .status(ConsignmentStatus.AUCTION_SCHEDULED)
                .build());
    Auction auction =
        auctionJpaRepository.saveAndFlush(
            Auction.builder()
                .consignment(consignment)
                .startedAt(LocalDateTime.now().minusHours(1))
                .endedAt(LocalDateTime.now().plusHours(1))
                .auctionStatus(AuctionStatus.ONGOING)
                .startingPrice(10_000L)
                .reservePrice(15_000L)
                .bidIncrement(500L)
                .build());
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    // when
    List<String> results;
    try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
      Future<String> firstResult =
          executorService.submit(
              () ->
                  placeBidAfterSignal(
                      auction.getAuctionId(), firstBidder.getMemberId(), 10_500L, ready, start));
      Future<String> secondResult =
          executorService.submit(
              () ->
                  placeBidAfterSignal(
                      auction.getAuctionId(), secondBidder.getMemberId(), 11_000L, ready, start));
      ready.await();
      start.countDown();
      results = List.of(firstResult.get(), secondResult.get());
    }

    // then
    List<Bid> bids =
        bidJpaRepository.findAll().stream()
            .filter(bid -> bid.getAuction().getAuctionId().equals(auction.getAuctionId()))
            .toList();
    List<Bid> highestBids =
        bids.stream().filter(bid -> bid.getBidStatus() == BidStatus.HIGHEST).toList();
    assertThat(results).allMatch(Set.of("SUCCESS", "OUTBID_EXISTS")::contains);
    assertThat(highestBids).singleElement().extracting(Bid::getBidPrice).isEqualTo(11_000L);
  }

  private Point createPointWithBalance(Long memberId, long balance) {
    Point point = Point.create(memberId);
    point.increaseBalance(balance);
    return point;
  }

  private String placeBidAfterSignal(
      Long auctionId, Long memberId, Long bidPrice, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    try {
      bidService.placeBid(auctionId, memberId, new PlaceBidRequest(bidPrice));
      return "SUCCESS";
    } catch (PickUpException exception) {
      return exception.getExceptionCodeName();
    }
  }
}
