package com.ootd.pickup.auction.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SellerStatsIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MemberJpaRepository memberJpaRepository;
  @Autowired private CardJpaRepository cardJpaRepository;
  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;
  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Test
  void 판매자의_상품_경매_상태별_건수를_집계한다() throws Exception {
    // given
    Member seller = createMember("seller");
    createConsignment(seller, ConsignmentStatus.REGISTERABLE);
    Consignment scheduled = createConsignment(seller, ConsignmentStatus.AUCTION_SCHEDULED);
    Consignment ongoing = createConsignment(seller, ConsignmentStatus.AUCTION_ONGOING);
    Consignment won = createConsignment(seller, ConsignmentStatus.WON);
    createAuction(scheduled, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(1));
    createAuction(ongoing, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1));
    createAuction(won, AuctionStatus.WON, LocalDateTime.now().minusHours(1));

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/stats")
                .requestAttr(
                    AuthenticationAttributes.ATTRIBUTE_NAME,
                    new Authentication(seller.getMemberId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registeredConsignments").value(1))
        .andExpect(jsonPath("$.scheduledAuctions").value(1))
        .andExpect(jsonPath("$.ongoingAuctions").value(1))
        .andExpect(jsonPath("$.wonConsignments").value(1));
  }

  private Member createMember(String nickname) {
    Member member = Member.create("login-" + nickname + System.nanoTime(), "password", nickname);
    return memberJpaRepository.save(member);
  }

  private Consignment createConsignment(Member seller, ConsignmentStatus status) {
    Card card =
        Card.builder()
            .cardName("리자몽")
            .cardNumber("4/102")
            .setName("Base Set")
            .language(Language.JAPANESE)
            .rarity(Rarity.MINT)
            .imageUrl("https://image.example.com/card.png")
            .build();
    cardJpaRepository.save(card);

    Consignment consignment =
        Consignment.builder().card(card).sellerMember(seller).status(status).build();
    return consignmentJpaRepository.save(consignment);
  }

  private Auction createAuction(
      Consignment consignment, AuctionStatus status, LocalDateTime endedAt) {
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusDays(1))
            .endedAt(endedAt)
            .auctionStatus(status)
            .startingPrice(10000L)
            .reservePrice(10000L)
            .bidIncrement(500L)
            .build();
    return auctionJpaRepository.save(auction);
  }
}
