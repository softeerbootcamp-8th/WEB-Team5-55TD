package com.ootd.pickup.bid.repository;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BidRepositoryIntegrationTest {

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private BidRepository bidRepository;

  @Test
  void 진행중인_경매에서_최고_입찰가를_가진_회원은_true를_반환한다() {
    // given
    Member seller = createMember("bid-highest-seller");
    Member member = createMember("bid-highest");
    Auction auction = createAuction(seller, AuctionStatus.ONGOING);
    placeHighestBid(auction, member, 10_000L);

    // when
    boolean hasCurrentHighestBid =
        bidRepository.existsCurrentHighestBidByMemberId(member.getMemberId());

    // then
    assertThat(hasCurrentHighestBid).isTrue();
  }

  @Test
  void 더_높은_입찰에_추월당한_회원은_false를_반환한다() {
    // given
    Member seller = createMember("bid-outbid-seller");
    Member outbidMember = createMember("bid-outbid");
    Member higherMember = createMember("bid-higher");
    Auction auction = createAuction(seller, AuctionStatus.ONGOING);
    bidRepository.save(Bid.create(auction, outbidMember, 10_000L));
    placeHighestBid(auction, higherMember, 10_500L);

    // when
    boolean outbidMemberHasCurrentHighestBid =
        bidRepository.existsCurrentHighestBidByMemberId(outbidMember.getMemberId());
    boolean higherMemberHasCurrentHighestBid =
        bidRepository.existsCurrentHighestBidByMemberId(higherMember.getMemberId());

    // then
    assertThat(outbidMemberHasCurrentHighestBid).isFalse();
    assertThat(higherMemberHasCurrentHighestBid).isTrue();
  }

  @Test
  void 종료된_경매의_최고_입찰자는_false를_반환한다() {
    // given
    Member seller = createMember("bid-ended-seller");
    Member member = createMember("bid-ended");
    Auction auction = createAuction(seller, AuctionStatus.WON);
    placeHighestBid(auction, member, 10_000L);

    // when
    boolean hasCurrentHighestBid =
        bidRepository.existsCurrentHighestBidByMemberId(member.getMemberId());

    // then
    assertThat(hasCurrentHighestBid).isFalse();
  }

  @Test
  void 입찰한_적이_없는_회원은_false를_반환한다() {
    // given
    Member member = createMember("bid-none");

    // when
    boolean hasCurrentHighestBid =
        bidRepository.existsCurrentHighestBidByMemberId(member.getMemberId());

    // then
    assertThat(hasCurrentHighestBid).isFalse();
  }

  private Member createMember(String loginId) {
    return memberJpaRepository.save(Member.create(loginId, "password", loginId + "-nickname"));
  }

  private Bid placeHighestBid(Auction auction, Member member, Long bidPrice) {
    Bid bid = bidRepository.save(Bid.create(auction, member, bidPrice));
    auction.updateWinningBid(bid.getBidId(), bid.getBidPrice());
    auctionJpaRepository.save(auction);
    return bid;
  }

  private Auction createAuction(Member sellerMember, AuctionStatus auctionStatus) {
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName("입찰 테스트 카드")
                .cardNumber("1/100")
                .setName("입찰 테스트 세트")
                .language(Language.KOREAN)
                .rarity(Rarity.RARE_HOLO)
                .imageUrl("https://example.com/bid.png")
                .build());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(sellerMember)
                .status(ConsignmentStatus.IN_AUCTION)
                .build());
    return auctionJpaRepository.save(
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusHours(1))
            .auctionStatus(auctionStatus)
            .startingPrice(5_000L)
            .reservePrice(8_000L)
            .bidIncrement(500L)
            .build());
  }
}
