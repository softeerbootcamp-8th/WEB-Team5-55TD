package com.ootd.pickup.auction.repository.auction;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionJpaRepositoryTest {

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private EntityManager entityManager;

  @Test
  void 현재가보다_높고_최소단위_이상이면_현재가가_갱신되고_1이_반환된다() {
    // given
    Auction auction = createAuction("cas-success");

    // when
    int updated = auctionJpaRepository.updateCurrentPriceIfHigher(auction.getAuctionId(), 10_500L);

    // then
    entityManager.clear();
    Auction reloaded = auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow();
    assertThat(updated).isEqualTo(1);
    assertThat(reloaded.getCurrentPrice()).isEqualTo(10_500L);
  }

  @Test
  void 현재가보다_높지_않으면_갱신되지_않고_0이_반환된다() {
    // given
    Auction auction = createAuction("cas-not-higher");

    // when
    int updated = auctionJpaRepository.updateCurrentPriceIfHigher(auction.getAuctionId(), 10_000L);

    // then
    entityManager.clear();
    Auction reloaded = auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow();
    assertThat(updated).isEqualTo(0);
    assertThat(reloaded.getCurrentPrice()).isEqualTo(10_000L);
  }

  @Test
  void 최소단위_미만이면_갱신되지_않고_0이_반환된다() {
    // given
    Auction auction = createAuction("cas-below-increment");

    // when
    int updated = auctionJpaRepository.updateCurrentPriceIfHigher(auction.getAuctionId(), 10_100L);

    // then
    entityManager.clear();
    Auction reloaded = auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow();
    assertThat(updated).isEqualTo(0);
    assertThat(reloaded.getCurrentPrice()).isEqualTo(10_000L);
  }

  private Auction createAuction(String cardName) {
    Member seller = memberJpaRepository.save(Member.create(cardName, "password", cardName));
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName(cardName)
                .cardNumber(cardName)
                .setName("현재가 테스트 세트")
                .language(Language.KOREAN)
                .rarity(Rarity.MINT)
                .imageUrl("https://example.com/current-price.png")
                .build());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(seller)
                .status(ConsignmentStatus.AUCTION_SCHEDULED)
                .build());
    return auctionJpaRepository.save(
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(LocalDateTime.now().plusHours(1))
            .auctionStatus(AuctionStatus.ONGOING)
            .startingPrice(10_000L)
            .reservePrice(15_000L)
            .bidIncrement(500L)
            .build());
  }
}
