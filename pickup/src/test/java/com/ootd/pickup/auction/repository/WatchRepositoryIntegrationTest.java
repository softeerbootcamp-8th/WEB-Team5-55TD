package com.ootd.pickup.auction.repository;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.domain.Watch;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.auction.repository.watch.WatchJpaRepository;
import com.ootd.pickup.auction.repository.watch.WatchRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WatchRepositoryIntegrationTest {

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private WatchJpaRepository watchJpaRepository;

  @Autowired private WatchRepository watchRepository;

  @Test
  void 같은_회원과_경매의_관심은_중복저장할_수_없다() {
    // given
    Member member = createMember("watch-duplicate");
    Auction auction = createAuction(member, "중복 관심 카드");
    watchJpaRepository.saveAndFlush(Watch.builder().member(member).auction(auction).build());

    // when & then
    assertThatThrownBy(
            () ->
                watchJpaRepository.saveAndFlush(
                    Watch.builder().member(member).auction(auction).build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 관심해제는_회원과_경매가_모두_일치하는_관심만_삭제한다() {
    // given
    Member firstMember = createMember("watch-owner");
    Member secondMember = createMember("watch-other");
    Auction firstAuction = createAuction(firstMember, "첫 번째 관심 카드");
    Auction secondAuction = createAuction(firstMember, "두 번째 관심 카드");
    watchJpaRepository.save(Watch.builder().member(firstMember).auction(firstAuction).build());
    watchJpaRepository.save(Watch.builder().member(firstMember).auction(secondAuction).build());
    watchJpaRepository.save(Watch.builder().member(secondMember).auction(firstAuction).build());
    watchJpaRepository.flush();

    // when
    int deletedCount =
        watchRepository.deleteByMemberIdAndAuctionId(
            firstMember.getMemberId(), firstAuction.getAuctionId());

    // then
    assertThat(deletedCount).isEqualTo(1);
    assertThat(watchJpaRepository.findAll())
        .extracting(
            watch -> watch.getMember().getMemberId(), watch -> watch.getAuction().getAuctionId())
        .containsExactlyInAnyOrder(
            tuple(firstMember.getMemberId(), secondAuction.getAuctionId()),
            tuple(secondMember.getMemberId(), firstAuction.getAuctionId()));
  }

  private Member createMember(String loginId) {
    return memberJpaRepository.save(Member.create(loginId, "password", loginId + "-nickname"));
  }

  private Auction createAuction(Member sellerMember, String cardName) {
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName(cardName)
                .cardNumber(cardName)
                .setName("관심 테스트 세트")
                .language(Language.KOREAN)
                .rarity(Rarity.MINT)
                .imageUrl("https://example.com/watch.png")
                .build());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(sellerMember)
                .status(ConsignmentStatus.AUCTION_SCHEDULED)
                .build());
    return auctionJpaRepository.save(
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().plusDays(1))
            .auctionStatus(AuctionStatus.SCHEDULED)
            .startingPrice(10000L)
            .reservePrice(15000L)
            .bidIncrement(500L)
            .build());
  }
}
