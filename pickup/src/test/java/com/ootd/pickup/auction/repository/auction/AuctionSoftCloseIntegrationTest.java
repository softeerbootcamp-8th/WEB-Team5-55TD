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
class AuctionSoftCloseIntegrationTest {

  @Autowired private AuctionRepository auctionRepository;
  @Autowired private AuctionJpaRepository auctionJpaRepository;
  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;
  @Autowired private MemberJpaRepository memberJpaRepository;
  @Autowired private CardJpaRepository cardJpaRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void 남은_시간이_5분_미만이면_한_번만_기존_종료시각에_5분을_추가한다() {
    LocalDateTime bidAt = LocalDateTime.of(2026, 8, 12, 10, 0);
    LocalDateTime originalEndAt = bidAt.plusMinutes(4);
    Auction auction = saveAuction(originalEndAt);

    boolean firstExtended = auctionRepository.extendEndAtIfClosingSoon(auction, bidAt);
    boolean secondExtended =
        auctionRepository.extendEndAtIfClosingSoon(auction, bidAt.plusSeconds(1));

    assertThat(firstExtended).isTrue();
    assertThat(secondExtended).isFalse();
    assertThat(auction.getEndedAt()).isEqualTo(originalEndAt.plusMinutes(5));

    entityManager.flush();
    entityManager.clear();
    assertThat(auctionJpaRepository.findById(auction.getAuctionId()).orElseThrow().getEndedAt())
        .isEqualTo(originalEndAt.plusMinutes(5));
  }

  @Test
  void 남은_시간이_정확히_5분이면_연장하지_않는다() {
    LocalDateTime bidAt = LocalDateTime.of(2026, 8, 12, 10, 0);
    LocalDateTime originalEndAt = bidAt.plusMinutes(5);
    Auction auction = saveAuction(originalEndAt);

    boolean extended = auctionRepository.extendEndAtIfClosingSoon(auction, bidAt);

    assertThat(extended).isFalse();
    assertThat(auction.getEndedAt()).isEqualTo(originalEndAt);
  }

  private Auction saveAuction(LocalDateTime endedAt) {
    Member seller = memberJpaRepository.save(Member.create("seller", "password", "판매자"));
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName("테스트 카드")
                .cardNumber("001")
                .setName("테스트 세트")
                .language(Language.KOREAN)
                .rarity(Rarity.RARE_HOLO)
                .imageUrl("https://example.com/card.png")
                .build());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(seller)
                .status(ConsignmentStatus.IN_AUCTION)
                .build());
    return auctionJpaRepository.saveAndFlush(
        Auction.builder()
            .consignment(consignment)
            .startedAt(endedAt.minusDays(7))
            .endedAt(endedAt)
            .auctionStatus(AuctionStatus.ONGOING)
            .startingPrice(10_000L)
            .reservePrice(15_000L)
            .bidIncrement(500L)
            .build());
  }
}
