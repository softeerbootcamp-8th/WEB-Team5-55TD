package com.ootd.pickup.auction.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SellerStatsIntegrationTest extends SellerAuctionFixtureSupport {

  @Autowired private MockMvc mockMvc;

  @Test
  void 판매자의_상품_경매_상태별_건수를_집계한다() throws Exception {
    // given
    Member seller = createMember("seller");
    createConsignment(seller, ConsignmentStatus.REGISTERABLE);
    Consignment scheduled = createConsignment(seller, ConsignmentStatus.AUCTION_SCHEDULED);
    Consignment ongoing = createConsignment(seller, ConsignmentStatus.AUCTION_ONGOING);
    Consignment won = createConsignment(seller, ConsignmentStatus.WON);
    createAuction(scheduled, AuctionStatus.SCHEDULED, 10000L, LocalDateTime.now().plusDays(1));
    createAuction(ongoing, AuctionStatus.ONGOING, 10000L, LocalDateTime.now().plusHours(1));
    createAuction(won, AuctionStatus.WON, 10000L, LocalDateTime.now().minusHours(1));

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
}
