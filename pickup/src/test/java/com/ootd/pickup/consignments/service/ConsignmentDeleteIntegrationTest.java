package com.ootd.pickup.consignments.service;

import static org.assertj.core.api.Assertions.*;

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
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유찰 후 REGISTERABLE로 되돌아간 상품을 삭제하면 과거 경매 행이 FK(fk_auction_consignment, ON DELETE 미지정 = RESTRICT)로
 * 여전히 참조 중이라 DataIntegrityViolationException이 발생하고, 이는 잡히지 않아 500으로 이어지던 문제(OOTD-535)의 회귀 테스트다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConsignmentDeleteIntegrationTest {

  @Autowired private ConsignmentService consignmentService;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private EntityManager entityManager;

  @Test
  void 유찰_이력이_있는_REGISTERABLE_상품을_삭제하면_무결성_위반_대신_비즈니스_예외가_발생한다() {
    // given
    Member seller = memberJpaRepository.save(Member.create("loginId", "password", "피카츄"));
    Card card = cardJpaRepository.save(createCard());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(seller)
                .majorDefect(null)
                .status(ConsignmentStatus.REGISTERABLE)
                .build());
    auctionJpaRepository.save(
        Auction.builder()
            .consignment(consignment)
            .title("리자몽 경매")
            .description("설명")
            .startedAt(LocalDateTime.now().minusDays(2))
            .endedAt(LocalDateTime.now().minusDays(1))
            .auctionStatus(AuctionStatus.PASSED)
            .startingPrice(1000L)
            .reservePrice(10000L)
            .bidIncrement(1000L)
            .build());
    entityManager.flush();

    // when & then
    assertThatThrownBy(
            () -> {
              consignmentService.deleteConsignment(
                  consignment.getConsignmentId(), seller.getMemberId());
              entityManager.flush();
            })
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.CONSIGNMENT_NOT_DELETABLE.getMessage());

    assertThat(consignmentJpaRepository.findById(consignment.getConsignmentId())).isPresent();
  }

  private Card createCard() {
    return Card.builder()
        .cardName("리자몽 1st Edition Holo")
        .cardNumber("4/102")
        .setName("Base Set")
        .language(Language.JAPANESE)
        .rarity(Rarity.RARE_HOLO)
        .imageUrl("https://image.example.com/card.png")
        .build();
  }
}
