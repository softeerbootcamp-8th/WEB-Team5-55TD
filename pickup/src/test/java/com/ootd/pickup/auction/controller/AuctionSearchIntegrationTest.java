package com.ootd.pickup.auction.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.domain.Watch;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
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
  @Autowired private AuctionRepository auctionRepository;
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
  void 가격_정렬은_화면에_보이는_현재가를_기준으로_한다() throws Exception {
    // given — 진행 중 경매는 입찰이 붙으면 현재가(winningPrice)가 시작가와 달라진다.
    Consignment consignment = createConsignment();
    Auction noBid = createAuction(consignment, AuctionStatus.ONGOING, 5000L, null);
    Auction highestBid = createAuction(consignment, AuctionStatus.ONGOING, 1000L, null);
    highestBid.updateWinningBid(1L, 9000L);
    Auction midBid = createAuction(consignment, AuctionStatus.ONGOING, 3000L, null);
    auctionJpaRepository.flush();

    // when & then — 현재가 3000 < 5000 < 9000 순서여야 한다.
    // 시작가 기준이면 highestBid(1000)가 맨 앞에 와서 화면에는 9000원이 먼저 보인다.
    mockMvc
        .perform(get("/auctions").param("sort", "PRICE_ASC").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].auctionId").value(midBid.getAuctionId()))
        .andExpect(jsonPath("$.items[0].currentPrice").value(3000))
        .andExpect(jsonPath("$.items[1].auctionId").value(noBid.getAuctionId()))
        .andExpect(jsonPath("$.items[1].currentPrice").value(5000))
        .andExpect(jsonPath("$.items[2].auctionId").value(highestBid.getAuctionId()))
        .andExpect(jsonPath("$.items[2].currentPrice").value(9000));
  }

  @Test
  void 가격_내림차순도_현재가를_기준으로_한다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    Auction noBid = createAuction(consignment, AuctionStatus.ONGOING, 5000L, null);
    Auction highestBid = createAuction(consignment, AuctionStatus.ONGOING, 1000L, null);
    highestBid.updateWinningBid(1L, 9000L);
    auctionJpaRepository.flush();

    // when & then
    mockMvc
        .perform(get("/auctions").param("sort", "PRICE_DESC").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].auctionId").value(highestBid.getAuctionId()))
        .andExpect(jsonPath("$.items[1].auctionId").value(noBid.getAuctionId()));
  }

  @Test
  void 현재가_정렬에서도_커서로_다음_페이지가_이어진다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    Auction noBid = createAuction(consignment, AuctionStatus.ONGOING, 5000L, null);
    Auction highestBid = createAuction(consignment, AuctionStatus.ONGOING, 1000L, null);
    highestBid.updateWinningBid(1L, 9000L);
    Auction midBid = createAuction(consignment, AuctionStatus.ONGOING, 3000L, null);
    auctionJpaRepository.flush();

    // when
    String firstPage =
        mockMvc
            .perform(get("/auctions").param("sort", "PRICE_ASC").param("size", "2"))
            .andExpect(jsonPath("$.items[0].auctionId").value(midBid.getAuctionId()))
            .andExpect(jsonPath("$.items[1].auctionId").value(noBid.getAuctionId()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // then — 커서 다음 장에 남은 한 건만 오고 중복되지 않는다.
    mockMvc
        .perform(
            get("/auctions")
                .param("sort", "PRICE_ASC")
                .param("size", "2")
                .param("cursor", extractCursor(firstPage)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(highestBid.getAuctionId()));
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
  void 종료_임박순은_먼저_끝나는_순서이고_종료시각이_없으면_뒤로_보낸다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    LocalDateTime base = LocalDateTime.now().plusHours(1);
    Auction last = createAuction(consignment, AuctionStatus.ONGOING, 1000L, base.plusHours(5));
    Auction first = createAuction(consignment, AuctionStatus.ONGOING, 1000L, base);
    Auction noEndAt = createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("sort", "ENDING_SOON").param("size", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].auctionId").value(first.getAuctionId()))
        .andExpect(jsonPath("$.items[1].auctionId").value(last.getAuctionId()))
        .andExpect(jsonPath("$.items[2].auctionId").value(noEndAt.getAuctionId()));
  }

  @Test
  void 시작_임박순은_먼저_시작하는_순서다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    LocalDateTime base = LocalDateTime.now().plusDays(1);
    Auction later = createScheduledAuction(consignment, base.plusDays(3));
    Auction sooner = createScheduledAuction(consignment, base);

    // when & then
    mockMvc
        .perform(get("/auctions").param("sort", "STARTING_SOON").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].auctionId").value(sooner.getAuctionId()))
        .andExpect(jsonPath("$.items[1].auctionId").value(later.getAuctionId()));
  }

  @Test
  void 최신순은_나중에_등록된_경매가_먼저_온다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    Auction older = createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null);
    Auction newer = createAuction(consignment, AuctionStatus.SCHEDULED, 2000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("sort", "RECENT").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].auctionId").value(newer.getAuctionId()))
        .andExpect(jsonPath("$.items[1].auctionId").value(older.getAuctionId()));
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
  void 검색어의_LIKE_와일드카드는_리터럴로_취급된다() throws Exception {
    // given
    Card underscoreCard = createCard("리자몽_EX", "4/102", "Base Set", Language.JAPANESE);
    Card anyCharCard = createCard("리자몽1EX", "5/102", "Base Set", Language.JAPANESE);
    Auction underscoreAuction =
        createAuction(createConsignment(underscoreCard), AuctionStatus.SCHEDULED, 1000L, null);
    createAuction(createConsignment(anyCharCard), AuctionStatus.SCHEDULED, 2000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("q", "리자몽_EX"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(underscoreAuction.getAuctionId()));
  }

  @Test
  void 검색어가_퍼센트뿐이면_전체가_아니라_아무것도_찾지_못한다() throws Exception {
    // given
    createAuction(
        createConsignment(createCard("리자몽", "4/102", "Base Set", Language.JAPANESE)),
        AuctionStatus.SCHEDULED,
        1000L,
        null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("q", "%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void 상태를_여러_개_지정하면_OR로_합쳐서_조회한다() throws Exception {
    // given — 종료 탭 하나가 WON·PASSED 두 상태를 함께 보내는 것과 같은 형태다.
    Consignment consignment = createConsignment();
    Auction won = createAuction(consignment, AuctionStatus.WON, 1000L, null);
    Auction passed = createAuction(createConsignment(), AuctionStatus.PASSED, 2000L, null);
    createAuction(createConsignment(), AuctionStatus.ONGOING, 3000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("status", "WON").param("status", "PASSED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(
            jsonPath("$.items[*].auctionId")
                .value(
                    org.hamcrest.Matchers.containsInAnyOrder(
                        won.getAuctionId().intValue(), passed.getAuctionId().intValue())));
  }

  @Test
  void 같은_상태를_여러_번_보내도_결과는_같다() throws Exception {
    // given
    createAuction(createConsignment(), AuctionStatus.ONGOING, 1000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("status", "ONGOING").param("status", "ONGOING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void 상태를_상태_종류보다_많이_보내면_400을_반환한다() throws Exception {
    // when & then
    mockMvc
        .perform(
            get("/auctions")
                .param("status", "ONGOING")
                .param("status", "SCHEDULED")
                .param("status", "WON")
                .param("status", "PASSED")
                .param("status", "ONGOING"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 알_수_없는_상태를_보내면_400을_반환한다() throws Exception {
    // when & then
    mockMvc.perform(get("/auctions").param("status", "UNKNOWN")).andExpect(status().isBadRequest());
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

  @Test
  void sellerId로_필터링하면_해당_판매자의_경매만_조회된다() throws Exception {
    // given
    Consignment sellerAConsignment = createConsignment();
    Auction sellerAAuction1 =
        createAuction(sellerAConsignment, AuctionStatus.SCHEDULED, 1000L, null);
    Auction sellerAAuction2 =
        createAuction(sellerAConsignment, AuctionStatus.SCHEDULED, 2000L, null);
    Consignment sellerBConsignment = createConsignment();
    createAuction(sellerBConsignment, AuctionStatus.SCHEDULED, 3000L, null);
    Long sellerAId = sellerAConsignment.getSellerMember().getMemberId();

    // when & then
    mockMvc
        .perform(
            get("/auctions")
                .param("sellerId", String.valueOf(sellerAId))
                .param("sort", "PRICE_ASC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].auctionId").value(sellerAAuction1.getAuctionId()))
        .andExpect(jsonPath("$.items[1].auctionId").value(sellerAAuction2.getAuctionId()));
  }

  @Test
  void cardId로_필터링하면_같은_카드의_경매만_조회된다() throws Exception {
    // given
    Card sharedCard = createCard("리자몽", "4/102", "Base Set", Language.JAPANESE);
    Consignment consignment1 =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(sharedCard)
                .sellerMember(createMember("cardFilterSeller1"))
                .status(ConsignmentStatus.IN_AUCTION)
                .build());
    Consignment consignment2 =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(sharedCard)
                .sellerMember(createMember("cardFilterSeller2"))
                .status(ConsignmentStatus.IN_AUCTION)
                .build());
    Auction sameCardAuction1 = createAuction(consignment1, AuctionStatus.SCHEDULED, 1000L, null);
    Auction sameCardAuction2 = createAuction(consignment2, AuctionStatus.SCHEDULED, 2000L, null);
    Consignment otherConsignment = createConsignment();
    createAuction(otherConsignment, AuctionStatus.SCHEDULED, 3000L, null);

    // when & then
    mockMvc
        .perform(
            get("/auctions")
                .param("cardId", String.valueOf(sharedCard.getCardId()))
                .param("sort", "PRICE_ASC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].auctionId").value(sameCardAuction1.getAuctionId()))
        .andExpect(jsonPath("$.items[1].auctionId").value(sameCardAuction2.getAuctionId()));
  }

  @Test
  void excludeAuctionId를_지정하면_해당_경매는_결과에서_제외된다() throws Exception {
    // given
    Consignment consignment = createConsignment();
    Auction current = createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null);
    Auction other = createAuction(consignment, AuctionStatus.SCHEDULED, 2000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("excludeAuctionId", String.valueOf(current.getAuctionId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(other.getAuctionId()));
  }

  @Test
  void searchField가_AUCTION_TITLE이면_경매명으로만_찾는다() throws Exception {
    // given
    Card card = createCard("리자몽", "4/102", "Base Set", Language.JAPANESE);
    Consignment consignment = createConsignment(card, createMember("titleSeller"));
    Auction titled = createAuction(consignment, AuctionStatus.SCHEDULED, 1000L, null, "리자몽 단독 출품전");
    createAuction(consignment, AuctionStatus.SCHEDULED, 2000L, null, "피카츄 기획전");

    // when & then
    mockMvc
        .perform(get("/auctions").param("q", "단독 출품전").param("searchField", "AUCTION_TITLE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(titled.getAuctionId()));

    mockMvc
        .perform(get("/auctions").param("q", "리자몽").param("searchField", "AUCTION_TITLE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(titled.getAuctionId()));
  }

  @Test
  void searchField가_CARD_NAME이면_세트명은_찾지_못한다() throws Exception {
    // given
    Card card = createCard("리자몽", "4/102", "정글 컬렉션", Language.JAPANESE);
    Auction auction =
        createAuction(
            createConsignment(card, createMember("cardNameSeller")),
            AuctionStatus.SCHEDULED,
            1000L,
            null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("q", "리자몽").param("searchField", "CARD_NAME"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(auction.getAuctionId()));

    mockMvc
        .perform(get("/auctions").param("q", "정글 컬렉션").param("searchField", "CARD_NAME"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void searchField가_SELLER이면_판매자_닉네임으로_찾는다() throws Exception {
    // given
    Card card = createCard("리자몽", "4/102", "Base Set", Language.JAPANESE);
    Auction target =
        createAuction(
            createConsignment(card, createMember("포켓몬마스터민제")),
            AuctionStatus.SCHEDULED,
            1000L,
            null);
    createAuction(
        createConsignment(card, createMember("다른판매자")), AuctionStatus.SCHEDULED, 2000L, null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("q", "마스터민제").param("searchField", "SELLER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].auctionId").value(target.getAuctionId()));
  }

  @Test
  void searchField를_생략하면_경매명과_판매자까지_함께_훑는다() throws Exception {
    // given
    Card card = createCard("피카츄", "025/102", "Jungle", Language.KOREAN);
    Auction byTitle =
        createAuction(
            createConsignment(card, createMember("allFieldSellerA")),
            AuctionStatus.SCHEDULED,
            1000L,
            null,
            "리자몽 기획전");
    Auction bySeller =
        createAuction(
            createConsignment(card, createMember("리자몽수집가")), AuctionStatus.SCHEDULED, 2000L, null);
    createAuction(
        createConsignment(card, createMember("allFieldSellerC")),
        AuctionStatus.SCHEDULED,
        3000L,
        null);

    // when & then
    mockMvc
        .perform(get("/auctions").param("q", "리자몽").param("sort", "PRICE_ASC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].auctionId").value(byTitle.getAuctionId()))
        .andExpect(jsonPath("$.items[1].auctionId").value(bySeller.getAuctionId()));
  }

  @Test
  void 지원하지_않는_searchField면_400을_반환한다() throws Exception {
    // when & then
    mockMvc
        .perform(get("/auctions").param("q", "리자몽").param("searchField", "SELLER_EMAIL"))
        .andExpect(status().isBadRequest());
  }

  private String extractCursor(String json) {
    JsonNode root = objectMapper.readTree(json);
    return root.get("cursor").asText();
  }

  private Consignment createConsignment() {
    return createConsignment(createCard("리자몽", "4/102", "Base Set", Language.JAPANESE));
  }

  private Consignment createConsignment(Card card) {
    return createConsignment(card, createMember("seller" + System.identityHashCode(card)));
  }

  private Consignment createConsignment(Card card, Member seller) {
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
            .rarity(Rarity.RARE_HOLO)
            .imageUrl("https://image.example.com/" + cardNumber + ".png")
            .build();
    return cardJpaRepository.save(card);
  }

  private Member createMember(String nickname) {
    Member member = Member.create("login-" + nickname, "password", nickname);
    return memberJpaRepository.save(member);
  }

  private Auction createScheduledAuction(Consignment consignment, LocalDateTime startedAt) {
    Auction auction =
        Auction.builder()
            .title("테스트 제목")
            .description("테스트 설명")
            .consignment(consignment)
            .startedAt(startedAt)
            .endedAt(null)
            .auctionStatus(AuctionStatus.SCHEDULED)
            .startingPrice(1000L)
            .reservePrice(1000L)
            .bidIncrement(50L)
            .build();
    return auctionJpaRepository.save(auction);
  }

  private Auction createAuction(
      Consignment consignment, AuctionStatus status, Long startingPrice, LocalDateTime endedAt) {
    return createAuction(consignment, status, startingPrice, endedAt, "테스트 제목");
  }

  private Auction createAuction(
      Consignment consignment,
      AuctionStatus status,
      Long startingPrice,
      LocalDateTime endedAt,
      String title) {
    Auction auction =
        Auction.builder()
            .title(title)
            .description("테스트 설명")
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
    watchJpaRepository.flush();
    auctionRepository.incrementWatchCountById(auction.getAuctionId());
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
