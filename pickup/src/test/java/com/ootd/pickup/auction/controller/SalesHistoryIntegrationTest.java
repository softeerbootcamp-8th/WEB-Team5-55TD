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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesHistoryIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private MemberJpaRepository memberJpaRepository;
  @Autowired private CardJpaRepository cardJpaRepository;
  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;
  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Test
  void 판매자의_종료된_경매만_최신순으로_조회한다() throws Exception {
    // given
    Member seller = createMember("seller");
    Consignment consignment = createConsignment(seller);
    Auction won =
        createAuction(consignment, AuctionStatus.WON, 12000L, LocalDateTime.now().minusHours(1));
    Auction passed =
        createAuction(consignment, AuctionStatus.PASSED, 5000L, LocalDateTime.now().minusHours(2));
    createAuction(consignment, AuctionStatus.ONGOING, 3000L, null);

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/sales")
                .requestAttr(
                    AuthenticationAttributes.ATTRIBUTE_NAME,
                    new Authentication(seller.getMemberId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].auctionId").value(won.getAuctionId()))
        .andExpect(jsonPath("$.items[0].resultType").value("WON"))
        .andExpect(jsonPath("$.items[1].auctionId").value(passed.getAuctionId()))
        .andExpect(jsonPath("$.items[1].resultType").value("PASSED"));
  }

  @Test
  void status로_낙찰_내역만_필터링한다() throws Exception {
    // given
    Member seller = createMember("seller");
    Consignment consignment = createConsignment(seller);
    Auction won =
        createAuction(consignment, AuctionStatus.WON, 12000L, LocalDateTime.now().minusHours(1));
    createAuction(consignment, AuctionStatus.PASSED, 5000L, LocalDateTime.now().minusHours(2));

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/sales")
                .param("status", "WON")
                .requestAttr(
                    AuthenticationAttributes.ATTRIBUTE_NAME,
                    new Authentication(seller.getMemberId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(won.getAuctionId()));
  }

  @Test
  void 다른_판매자의_판매_내역은_조회되지_않는다() throws Exception {
    // given
    Member seller = createMember("seller");
    Member otherSeller = createMember("otherSeller");
    Consignment consignment = createConsignment(seller);
    createAuction(consignment, AuctionStatus.WON, 12000L, LocalDateTime.now().minusHours(1));

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/sales")
                .requestAttr(
                    AuthenticationAttributes.ATTRIBUTE_NAME,
                    new Authentication(otherSeller.getMemberId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void 커서로_다음_페이지를_조회한다() throws Exception {
    // given
    Member seller = createMember("seller");
    Consignment consignment = createConsignment(seller);
    Auction newest =
        createAuction(consignment, AuctionStatus.WON, 1000L, LocalDateTime.now().minusHours(1));
    Auction oldest =
        createAuction(consignment, AuctionStatus.WON, 2000L, LocalDateTime.now().minusHours(2));

    String firstPage =
        mockMvc
            .perform(
                get("/sellers/me/sales")
                    .param("size", "1")
                    .requestAttr(
                        AuthenticationAttributes.ATTRIBUTE_NAME,
                        new Authentication(seller.getMemberId())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = extractCursor(firstPage);

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/sales")
                .param("size", "1")
                .param("cursor", cursor)
                .requestAttr(
                    AuthenticationAttributes.ATTRIBUTE_NAME,
                    new Authentication(seller.getMemberId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.cursor").doesNotExist())
        .andExpect(jsonPath("$.items[0].auctionId").value(oldest.getAuctionId()));
  }

  private String extractCursor(String json) {
    JsonNode root = objectMapper.readTree(json);
    return root.get("cursor").asText();
  }

  private Member createMember(String nickname) {
    Member member = Member.create("login-" + nickname + System.nanoTime(), "password", nickname);
    return memberJpaRepository.save(member);
  }

  private Consignment createConsignment(Member seller) {
    Card card =
        Card.builder()
            .cardName("리자몽")
            .cardNumber("4/102")
            .setName("Base Set")
            .language(Language.JAPANESE)
            .rarity(Rarity.RARE_HOLO)
            .imageUrl("https://image.example.com/card.png")
            .build();
    cardJpaRepository.save(card);

    Consignment consignment =
        Consignment.builder()
            .card(card)
            .sellerMember(seller)
            .status(ConsignmentStatus.AUCTION_ONGOING)
            .build();
    return consignmentJpaRepository.save(consignment);
  }

  private Auction createAuction(
      Consignment consignment, AuctionStatus status, Long startingPrice, LocalDateTime endedAt) {
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusDays(1))
            .endedAt(endedAt)
            .auctionStatus(status)
            .startingPrice(startingPrice)
            .reservePrice(startingPrice)
            .bidIncrement(Math.round(startingPrice * 0.05))
            .build();
    return auctionJpaRepository.save(auction);
  }
}
