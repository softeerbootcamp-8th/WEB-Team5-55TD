package com.ootd.pickup.consignments.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConsignmentRepositoryIntegrationTest {

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private ConsignmentRepository consignmentRepository;

  @Test
  void 경매상태로_조회하면_상품별_가장_최근_경매상태만_필터링한다() {
    // given
    Member seller =
        memberJpaRepository.save(Member.create("consignment-filter", "password", "필터셀러"));
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName("필터 카드")
                .cardNumber("001")
                .setName("필터 세트")
                .language(Language.KOREAN)
                .rarity(Rarity.RARE_HOLO)
                .imageUrl("https://example.com/filter.png")
                .build());
    Consignment scheduled = createConsignment(seller, card);
    Consignment reapplied = createConsignment(seller, card);
    createAuction(scheduled, AuctionStatus.SCHEDULED);
    createAuction(reapplied, AuctionStatus.PASSED);
    createAuction(reapplied, AuctionStatus.ONGOING);
    auctionJpaRepository.flush();

    // when
    List<Consignment> scheduledResult =
        consignmentRepository.findAllBySellerMemberIdAndStatusAndLatestAuctionStatusAndCursor(
            seller.getMemberId(), ConsignmentStatus.IN_AUCTION, AuctionStatus.SCHEDULED, null, 10);
    List<Consignment> ongoingResult =
        consignmentRepository.findAllBySellerMemberIdAndStatusAndLatestAuctionStatusAndCursor(
            seller.getMemberId(), ConsignmentStatus.IN_AUCTION, AuctionStatus.ONGOING, null, 10);
    List<Consignment> passedResult =
        consignmentRepository.findAllBySellerMemberIdAndStatusAndLatestAuctionStatusAndCursor(
            seller.getMemberId(), ConsignmentStatus.IN_AUCTION, AuctionStatus.PASSED, null, 10);

    // then
    assertThat(scheduledResult).containsExactly(scheduled);
    assertThat(ongoingResult).containsExactly(reapplied);
    assertThat(passedResult).isEmpty();
  }

  private Consignment createConsignment(Member seller, Card card) {
    return consignmentJpaRepository.save(
        Consignment.builder()
            .sellerMember(seller)
            .card(card)
            .status(ConsignmentStatus.IN_AUCTION)
            .build());
  }

  private void createAuction(Consignment consignment, AuctionStatus status) {
    auctionJpaRepository.save(
        Auction.builder()
            .consignment(consignment)
            .title("테스트 제목")
            .description("테스트 설명")
            .startedAt(LocalDateTime.now().plusDays(1))
            .auctionStatus(status)
            .startingPrice(10000L)
            .reservePrice(15000L)
            .bidIncrement(500L)
            .build());
  }
}
