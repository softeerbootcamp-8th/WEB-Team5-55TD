package com.ootd.pickup.auction.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.domain.Watch;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.auction.repository.watch.WatchJpaRepository;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.domain.Grade;
import com.ootd.pickup.consignments.repository.certificate.CertificateJpaRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.consignments.repository.consignmentImage.ConsignmentImageJpaRepository;
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
class AuctionSearchIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private MemberJpaRepository memberJpaRepository;
  @Autowired private CardJpaRepository cardJpaRepository;
  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;
  @Autowired private CertificateJpaRepository certificateJpaRepository;
  @Autowired private ConsignmentImageJpaRepository consignmentImageJpaRepository;
  @Autowired private AuctionJpaRepository auctionJpaRepository;
  @Autowired private WatchJpaRepository watchJpaRepository;

  @Test
  void 시작가_오름차순_정렬과_커서로_페이지를_조회한다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    Auction cheap = createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null);
    Auction mid = createAuction(consignment, AuctionStatus.SCHEDULED, 2000L, null);
    Auction expensive = createAuction(consignment, AuctionStatus.SCHEDULED, 3000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("sort", "PRICE_ASC").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.items[0].auctionId").value(cheap.getAuctionId()))
        .andExpect(jsonPath("$.items[1].auctionId").value(mid.getAuctionId()));

    String secondPageCursor =
        mockMvc
            .perform(get("/auctions").param("sort", "PRICE_ASC").param("size", "2"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = extractCursor(secondPageCursor);

    mockMvc
        .perform(
            get("/auctions").param("sort", "PRICE_ASC").param("size", "2").param("cursor", cursor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.cursor").doesNotExist())
        .andExpect(jsonPath("$.items[0].auctionId").value(expensive.getAuctionId()));
  }

  @Test
  void 관심수_내림차순으로_정렬한다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    Auction popular = createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null);
    Auction unpopular = createAuction(consignment, AuctionStatus.SCHEDULED, 2000L, null);
    Member watcher1 = createMember("watcher1");
    Member watcher2 = createMember("watcher2");
    createWatch(popular, watcher1);
    createWatch(popular, watcher2);

    // when & then
    mockMvc
        .perform(get("/auctions").param("sort", "POPULAR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].auctionId").value(popular.getAuctionId()))
        .andExpect(jsonPath("$.items[0].watchCount").value(2))
        .andExpect(jsonPath("$.items[1].auctionId").value(unpopular.getAuctionId()))
        .andExpect(jsonPath("$.items[1].watchCount").value(0));
  }

  @Test
  void 상태_필터로_경매를_좁힌다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    Auction scheduled = createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null);
    Auction ongoing =
        createAuction(consignment, AuctionStatus.ONGOING, 2000L, LocalDateTime.now().plusHours(1));

    // when & then
    mockMvc
        .perform(get("/auctions").param("status", "ONGOING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(ongoing.getAuctionId()));
  }

  @Test
  void 검색어로_카드명_세트명_언어를_찾는다() throws Exception {
    // given
    Card targetCard = createCard("리자몽 1st Edition Holo", "4/102", "Base Set", Language.JAPANESE);
    Card otherCard = createCard("피카츄", "025/102", "Jungle", Language.KOREAN);
    Consignment targetConsignment = createConsignment(targetCard);
    Consignment otherConsignment = createConsignment(otherCard);
    Auction targetAuction = createAuction(targetConsignment, AuctionStatus.SCHEDULED, 1000L, null);
    createAuction(otherConsignment, AuctionStatus.SCHEDULED, 2000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("q", "리자몽"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(targetAuction.getAuctionId()));

    mockMvc
        .perform(get("/auctions").param("q", "일본어"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(targetAuction.getAuctionId()));
  }

  @Test
  void limit이_있으면_커서_없이_상위_N개만_반환한다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null);
    createAuction(consignment, AuctionStatus.SCHEDULED, 2000L, null);
    createAuction(consignment, AuctionStatus.SCHEDULED, 3000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("limit", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.cursor").doesNotExist())
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void 관심등록_여부는_요청자별로_다르게_반환된다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    Auction auction = createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null);
    Member watcher = createMember("watcher");
    Member nonWatcher = createMember("nonWatcher");
    createWatch(auction, watcher);

    // when & then: watcher -> true
    mockMvc
        .perform(
            get("/auctions")
                .requestAttr(
                    AuthenticationAttributes.ATTRIBUTE_NAME,
                    new Authentication(watcher.getMemberId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].watched").value(true));

    // non-watcher -> false
    mockMvc
        .perform(
            get("/auctions")
                .requestAttr(
                    AuthenticationAttributes.ATTRIBUTE_NAME,
                    new Authentication(nonWatcher.getMemberId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].watched").value(false));

    // anonymous -> false
    mockMvc
        .perform(get("/auctions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].watched").value(false));
  }

  @Test
  void 썸네일은_위탁상품의_첫번째_이미지를_사용한다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    createConsignmentImage(consignment, 2, "media/consignments/1/second.png");
    createConsignmentImage(consignment, 1, "media/consignments/1/first.png");
    Auction auction = createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].auctionId").value(auction.getAuctionId()))
        .andExpect(
            jsonPath("$.items[0].thumbnailUrl")
                .value("https://images.test/media/consignments/1/first.png"));
  }

  @Test
  void 인증서_정보로_grade가_조합된다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    createCertificate(consignment, CertificationBody.PSA, Grade.GEM_MINT);
    Auction auction = createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].auctionId").value(auction.getAuctionId()))
        .andExpect(jsonPath("$.items[0].grade").value("PSA 10"));
  }

  private String extractCursor(String json) {
    JsonNode root = objectMapper.readTree(json);
    return root.get("cursor").asText();
  }

  private Consignment createConsignment() {
    return createConsignment(createCard("리자몽", "4/102", "Base Set", Language.JAPANESE));
  }

  private Consignment createConsignment(Card card) {
    Member seller = createMember("seller" + System.identityHashCode(card));
    Consignment consignment =
        Consignment.builder()
            .card(card)
            .sellerMember(seller)
            .status(ConsignmentStatus.IN_AUCTION)
            .build();
    return consignmentJpaRepository.save(consignment);
  }

  private Card createCard(String cardName, String cardNumber, String setName, Language language) {
    Card card =
        Card.builder()
            .cardName(cardName)
            .cardNumber(cardNumber)
            .setName(setName)
            .language(language)
            .rarity(Rarity.MINT)
            .imageUrl("https://image.example.com/" + cardNumber + ".png")
            .build();
    return cardJpaRepository.save(card);
  }

  private Member createMember(String nickname) {
    Member member = Member.create("login-" + nickname, "password", nickname);
    return memberJpaRepository.save(member);
  }

  private Auction createAuction(
      Consignment consignment, AuctionStatus status, Long startingPrice, LocalDateTime endedAt) {
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().plusDays(1))
            .endedAt(endedAt)
            .auctionStatus(status)
            .startingPrice(startingPrice)
            .reservePrice(startingPrice)
            .bidIncrement(Math.round(startingPrice * 0.05))
            .build();
    return auctionJpaRepository.save(auction);
  }

  private void createWatch(Auction auction, Member member) {
    watchJpaRepository.save(Watch.builder().auction(auction).member(member).build());
  }

  private void createConsignmentImage(Consignment consignment, int order, String url) {
    consignmentImageJpaRepository.save(
        ConsignmentImage.builder()
            .consignment(consignment)
            .imageOrder(order)
            .objectKey(url)
            .build());
  }

  private void createCertificate(
      Consignment consignment, CertificationBody certificationBody, Grade grade) {
    certificateJpaRepository.save(
        Certificate.builder()
            .serialNumber("SERIAL-" + System.identityHashCode(consignment))
            .consignment(consignment)
            .grade(grade)
            .certificationBody(certificationBody)
            .inspectedAt(java.time.LocalDate.of(2026, 1, 1))
            .build());
  }
}
