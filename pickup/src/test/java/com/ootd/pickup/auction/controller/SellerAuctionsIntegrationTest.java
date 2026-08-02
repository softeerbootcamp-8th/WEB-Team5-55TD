package com.ootd.pickup.auction.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.member.domain.Member;
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
class SellerAuctionsIntegrationTest extends SellerAuctionFixtureSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void 판매자의_진행중인_경매만_조회한다() throws Exception {
    // given
    Member seller = createMember("seller");
    Consignment consignment = createConsignment(seller, ConsignmentStatus.AUCTION_ONGOING);
    Auction ongoing =
        createAuction(consignment, AuctionStatus.ONGOING, 3000L, LocalDateTime.now().plusHours(1));
    createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null);
    createAuction(consignment, AuctionStatus.WON, 5000L, LocalDateTime.now().minusHours(1));

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/auctions")
                .requestAttr(
                    AuthenticationAttributes.ATTRIBUTE_NAME,
                    new Authentication(seller.getMemberId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(ongoing.getAuctionId()))
        .andExpect(jsonPath("$.items[0].auctionStatus").value("ONGOING"));
  }

  @Test
  void 다른_판매자의_진행중_경매는_조회되지_않는다() throws Exception {
    // given
    Member seller = createMember("seller");
    Member otherSeller = createMember("otherSeller");
    Consignment consignment = createConsignment(seller, ConsignmentStatus.AUCTION_ONGOING);
    createAuction(consignment, AuctionStatus.ONGOING, 3000L, LocalDateTime.now().plusHours(1));

    // when & then
    mockMvc
        .perform(
            get("/sellers/me/auctions")
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
    Consignment consignment = createConsignment(seller, ConsignmentStatus.AUCTION_ONGOING);
    // endedAt desc 정렬이므로 늦게 끝나는 경매가 첫 페이지, 먼저 끝나는 경매가 다음 페이지에 온다.
    Auction endingLater =
        createAuction(consignment, AuctionStatus.ONGOING, 1000L, LocalDateTime.now().plusHours(2));
    Auction endingSooner =
        createAuction(consignment, AuctionStatus.ONGOING, 2000L, LocalDateTime.now().plusHours(1));

    String firstPage =
        mockMvc
            .perform(
                get("/sellers/me/auctions")
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
            get("/sellers/me/auctions")
                .param("size", "1")
                .param("cursor", cursor)
                .requestAttr(
                    AuthenticationAttributes.ATTRIBUTE_NAME,
                    new Authentication(seller.getMemberId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.cursor").doesNotExist())
        .andExpect(jsonPath("$.items[0].auctionId").value(endingSooner.getAuctionId()));
  }

  private String extractCursor(String json) {
    JsonNode root = objectMapper.readTree(json);
    return root.get("cursor").asText();
  }
}
