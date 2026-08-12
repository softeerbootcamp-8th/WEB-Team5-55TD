package com.ootd.pickup.auction.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.request.CreateAuctionRequest;
import com.ootd.pickup.auction.dto.request.SearchAuctionsRequest;
import com.ootd.pickup.auction.dto.response.AuctionDetailResponse;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.dto.response.CreateAuctionResponse;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.auction.repository.watch.WatchRepository;
import com.ootd.pickup.auction.repository.watch.WatchSummary;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.domain.Grade;
import com.ootd.pickup.consignments.repository.certificate.CertificateRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import com.ootd.pickup.consignments.repository.consignmentImage.ConsignmentImageRepository;
import com.ootd.pickup.consignments.service.CertificateManageService;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.service.ImageUrlResolver;
import com.ootd.pickup.member.domain.Member;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

  @Mock private ConsignmentRepository consignmentRepository;

  @Mock private AuctionRepository auctionRepository;

  @Mock private CertificateRepository certificateRepository;

  @Mock private CertificateManageService certificateManageService;

  @Mock private ConsignmentImageRepository consignmentImageRepository;

  @Mock private WatchRepository watchRepository;

  @Mock private ImageUrlResolver imageUrlResolver;

  @Mock private BidRepository bidRepository;

  private AuctionService auctionService;

  @BeforeEach
  void setUp() {
    auctionService =
        new AuctionService(
            consignmentRepository,
            auctionRepository,
            certificateRepository,
            certificateManageService,
            consignmentImageRepository,
            watchRepository,
            imageUrlResolver,
            bidRepository);
    lenient()
        .when(imageUrlResolver.resolve(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void 유효한_요청으로_경매를_신청하면_경매가_생성된다() {
    // given
    Long memberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, memberId, ConsignmentStatus.REGISTERABLE, null);
    given(consignmentRepository.findConsignmentById(consignmentId))
        .willReturn(Optional.of(consignment));
    given(auctionRepository.save(any(Auction.class)))
        .willAnswer(
            invocation -> {
              Auction auction = invocation.getArgument(0);
              ReflectionTestUtils.setField(auction, "auctionId", 1L);
              return auction;
            });

    LocalDateTime scheduledStartAt = LocalDateTime.now().plusDays(1).withHour(13).withMinute(30);
    CreateAuctionRequest request =
        new CreateAuctionRequest(consignmentId, 10000L, 15000L, scheduledStartAt);

    // when
    CreateAuctionResponse response = auctionService.registerAuction(memberId, request);

    // then
    assertThat(response.auctionId()).isEqualTo(1L);
    assertThat(response.consignmentId()).isEqualTo(consignmentId);
    assertThat(response.auctionStatus()).isEqualTo(AuctionStatus.SCHEDULED);
    assertThat(response.startingPrice()).isEqualTo(10000L);
    assertThat(response.bidIncrement()).isEqualTo(500L);
    assertThat(response.startedAt()).isEqualTo(scheduledStartAt);
    assertThat(response.endedAt()).isEqualTo(scheduledStartAt.plusDays(7));
    assertThat(response.winningBidId()).isNull();
    assertThat(response.winningPrice()).isNull();
    assertThat(consignment.getStatus()).isEqualTo(ConsignmentStatus.IN_AUCTION);
  }

  @Test
  void 시작가가_100원으로_나누어떨어지지_않아도_최소_입찰_단위는_시작가의_5퍼센트_반올림이다() {
    // given
    Long memberId = 1L;
    Long consignmentId = 10L;
    Consignment consignment =
        createConsignment(consignmentId, memberId, ConsignmentStatus.REGISTERABLE, null);
    given(consignmentRepository.findConsignmentById(consignmentId))
        .willReturn(Optional.of(consignment));
    given(auctionRepository.save(any(Auction.class)))
        .willAnswer(
            invocation -> {
              Auction auction = invocation.getArgument(0);
              ReflectionTestUtils.setField(auction, "auctionId", 1L);
              return auction;
            });

    LocalDateTime scheduledStartAt = LocalDateTime.now().plusDays(1).withHour(13).withMinute(30);
    CreateAuctionRequest request =
        new CreateAuctionRequest(consignmentId, 12_345L, 20_000L, scheduledStartAt);

    // when
    CreateAuctionResponse response = auctionService.registerAuction(memberId, request);

    // then
    // 프론트의 minBidUnit(=Math.round(startPrice * 0.05))과 같은 값이어야 한다.
    assertThat(response.bidIncrement()).isEqualTo(617L);
  }

  @Test
  void 존재하지_않는_위탁상품이면_예외가_발생한다() {
    // given
    Long memberId = 1L;
    Long notExistConsignmentId = 999L;
    given(consignmentRepository.findConsignmentById(notExistConsignmentId))
        .willReturn(Optional.empty());

    CreateAuctionRequest request =
        new CreateAuctionRequest(
            notExistConsignmentId, 10000L, 15000L, LocalDateTime.now().plusDays(1));

    // when & then
    assertThatThrownBy(() -> auctionService.registerAuction(memberId, request))
        .isInstanceOf(PickUpException.class);
    then(auctionRepository).shouldHaveNoInteractions();
  }

  @Test
  void 판매자_본인이_아니면_예외가_발생한다() {
    // given
    Long sellerMemberId = 1L;
    Long requesterMemberId = 2L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, sellerMemberId, ConsignmentStatus.REGISTERABLE, null);
    given(consignmentRepository.findConsignmentById(consignmentId))
        .willReturn(Optional.of(consignment));

    CreateAuctionRequest request =
        new CreateAuctionRequest(consignmentId, 10000L, 15000L, LocalDateTime.now().plusDays(1));

    // when & then
    assertThatThrownBy(() -> auctionService.registerAuction(requesterMemberId, request))
        .isInstanceOf(PickUpException.class);
    then(auctionRepository).shouldHaveNoInteractions();
  }

  @Test
  void 이미_경매_신청_가능한_상태가_아니면_예외가_발생한다() {
    // given
    Long memberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, memberId, ConsignmentStatus.IN_AUCTION, null);
    given(consignmentRepository.findConsignmentById(consignmentId))
        .willReturn(Optional.of(consignment));

    CreateAuctionRequest request =
        new CreateAuctionRequest(consignmentId, 10000L, 15000L, LocalDateTime.now().plusDays(1));

    // when & then
    assertThatThrownBy(() -> auctionService.registerAuction(memberId, request))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            e ->
                assertThat(((PickUpException) e).getMessage())
                    .isEqualTo(ExceptionCode.CONSIGNMENT_NOT_REGISTERABLE.getMessage()));
    then(auctionRepository).shouldHaveNoInteractions();
  }

  @Test
  void 시작가가_너무_커서_최소_다음_입찰가_계산이_Long_범위를_넘으면_예외가_발생한다() {
    // given
    Long memberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, memberId, ConsignmentStatus.REGISTERABLE, null);
    given(consignmentRepository.findConsignmentById(consignmentId))
        .willReturn(Optional.of(consignment));

    CreateAuctionRequest request =
        new CreateAuctionRequest(
            consignmentId,
            9_223_372_036_000_000_000L,
            9_223_372_036_000_000_000L,
            LocalDateTime.now().plusDays(1));

    // when & then
    assertThatThrownBy(() -> auctionService.registerAuction(memberId, request))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            e ->
                assertThat(((PickUpException) e).getMessage())
                    .isEqualTo(ExceptionCode.STARTING_PRICE_TOO_LARGE.getMessage()));
    assertThat(consignment.getStatus()).isEqualTo(ConsignmentStatus.REGISTERABLE);
    then(auctionRepository).shouldHaveNoInteractions();
  }

  @Test
  void limit이_있으면_커서_없이_상위_N개만_반환한다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L, consignment, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(1), null);
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), isNull(), eq(3), any(), any(), any()))
        .willReturn(List.of(auction));
    stubEmptyAssemblyDependencies();

    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, "POPULAR", 3, null, null, null, null, null);

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        auctionService.searchAuctions(null, request);

    // then
    assertThat(response.hasNext()).isFalse();
    assertThat(response.cursor()).isNull();
    assertThat(response.items()).hasSize(1);
  }

  @Test
  void 결과가_size보다_많으면_hasNext가_true이고_커서가_생성된다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction first =
        createAuction(
            1L, consignment, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(1), null);
    Auction second =
        createAuction(
            2L, consignment, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(2), null);
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), isNull(), eq(2), any(), any(), any()))
        .willReturn(List.of(first, second));
    stubEmptyAssemblyDependencies();

    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, "RECENT", null, null, 1, null, null, null);

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        auctionService.searchAuctions(null, request);

    // then
    assertThat(response.hasNext()).isTrue();
    assertThat(response.cursor()).isNotBlank();
    assertThat(response.items()).extracting(AuctionListItemResponse::auctionId).containsExactly(1L);
  }

  @Test
  void 결과가_size_이하이면_hasNext가_false다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L, consignment, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(1), null);
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), isNull(), eq(21), any(), any(), any()))
        .willReturn(List.of(auction));
    stubEmptyAssemblyDependencies();

    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, null, null, null, null, null, null, null);

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        auctionService.searchAuctions(null, request);

    // then
    assertThat(response.hasNext()).isFalse();
    assertThat(response.cursor()).isNull();
  }

  @Test
  void 잘못된_정렬값이면_예외가_발생한다() {
    // given
    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, "INVALID_SORT", null, null, 20, null, null, null);

    // when & then
    assertThatThrownBy(() -> auctionService.searchAuctions(null, request))
        .isInstanceOf(PickUpException.class);
    then(auctionRepository).shouldHaveNoInteractions();
  }

  @Test
  void 잘못된_상태값이면_예외가_발생한다() {
    // given
    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, List.of("INVALID"), null, null, null, 20, null, null, null);

    // when & then
    assertThatThrownBy(() -> auctionService.searchAuctions(null, request))
        .isInstanceOf(PickUpException.class);
    then(auctionRepository).shouldHaveNoInteractions();
  }

  @Test
  void size가_1보다_작으면_예외가_발생한다() {
    // given
    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, null, null, null, 0, null, null, null);

    // when & then
    assertThatThrownBy(() -> auctionService.searchAuctions(null, request))
        .isInstanceOf(PickUpException.class);
    then(auctionRepository).shouldHaveNoInteractions();
  }

  @Test
  void limit이_1보다_작으면_예외가_발생한다() {
    // given
    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, null, 0, null, null, null, null, null);

    // when & then
    assertThatThrownBy(() -> auctionService.searchAuctions(null, request))
        .isInstanceOf(PickUpException.class);
    then(auctionRepository).shouldHaveNoInteractions();
  }

  @Test
  void 대표_경매를_조회하면_진행중인_경매_중_관심수가_가장_많은_경매를_반환한다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L,
            consignment,
            AuctionStatus.ONGOING,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1));
    given(
            auctionRepository.searchAuctions(
                isNull(),
                eq(List.of(AuctionStatus.ONGOING)),
                any(),
                isNull(),
                eq(1),
                isNull(),
                isNull(),
                isNull()))
        .willReturn(List.of(auction));
    given(watchRepository.findWatchSummariesByAuctionIds(any(), any()))
        .willReturn(Map.of(1L, new WatchSummary(10L, false)));
    given(certificateManageService.getCertificatesByConsignmentId(any())).willReturn(Map.of());
    given(
            consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
                any()))
        .willReturn(List.of());

    // when
    AuctionListItemResponse response = auctionService.getFeaturedAuction(null);

    // then
    assertThat(response.auctionId()).isEqualTo(1L);
    assertThat(response.auctionStatus()).isEqualTo(AuctionStatus.ONGOING);
    assertThat(response.watchCount()).isEqualTo(10L);
  }

  @Test
  void 진행중인_경매가_없으면_대표_경매_조회시_예외가_발생한다() {
    // given
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .willReturn(List.of());

    // when & then
    assertThatThrownBy(() -> auctionService.getFeaturedAuction(null))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            e ->
                assertThat(((PickUpException) e).getMessage())
                    .isEqualTo(ExceptionCode.FEATURED_AUCTION_NOT_FOUND.getMessage()));
  }

  @Test
  void 관심_등록한_경매는_watched가_true이고_관심수가_반영된다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L,
            consignment,
            AuctionStatus.ONGOING,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1));
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .willReturn(List.of(auction));
    given(certificateManageService.getCertificatesByConsignmentId(any())).willReturn(Map.of());
    given(
            consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
                any()))
        .willReturn(List.of());
    given(watchRepository.findWatchSummariesByAuctionIds(eq(9L), any()))
        .willReturn(Map.of(1L, new WatchSummary(3L, true)));

    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, null, 5, null, null, null, null, null);

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        auctionService.searchAuctions(9L, request);

    // then
    AuctionListItemResponse item = response.items().get(0);
    assertThat(item.watched()).isTrue();
    assertThat(item.watchCount()).isEqualTo(3L);
  }

  @Test
  void 비로그인_사용자는_watched가_false다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L, consignment, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(1), null);
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .willReturn(List.of(auction));
    given(certificateManageService.getCertificatesByConsignmentId(any())).willReturn(Map.of());
    given(
            consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
                any()))
        .willReturn(List.of());
    given(watchRepository.findWatchSummariesByAuctionIds(isNull(), any())).willReturn(Map.of());

    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, null, 5, null, null, null, null, null);

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        auctionService.searchAuctions(null, request);

    // then
    assertThat(response.items().get(0).watched()).isFalse();
  }

  @Test
  void 진행중이고_입찰이_없으면_남은시간이_계산되고_currentPrice는_시작가다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    LocalDateTime endedAt = LocalDateTime.now().plusMinutes(30);
    Auction auction =
        createAuction(
            1L, consignment, AuctionStatus.ONGOING, LocalDateTime.now().minusHours(1), endedAt);
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .willReturn(List.of(auction));
    stubEmptyAssemblyDependencies();

    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, null, 5, null, null, null, null, null);

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        auctionService.searchAuctions(null, request);

    // then
    AuctionListItemResponse item = response.items().get(0);
    assertThat(item.currentPrice()).isEqualTo(10000L);
    assertThat(item.remainingSeconds()).isNotNull();
    assertThat(item.remainingSeconds()).isCloseTo(30 * 60L, Offset.offset(5L));
  }

  @Test
  void 진행중이고_입찰이_있으면_currentPrice는_최고_입찰가다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L,
            consignment,
            AuctionStatus.ONGOING,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1));
    auction.updateWinningBid(50L, 12000L);
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .willReturn(List.of(auction));
    given(certificateManageService.getCertificatesByConsignmentId(any())).willReturn(Map.of());
    given(
            consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
                any()))
        .willReturn(List.of());
    given(watchRepository.findWatchSummariesByAuctionIds(any(), any())).willReturn(Map.of());

    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, null, 5, null, null, null, null, null);

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        auctionService.searchAuctions(null, request);

    // then
    assertThat(response.items().get(0).currentPrice()).isEqualTo(12000L);
  }

  @Test
  void 예정_상태면_남은시간과_currentPrice가_null이다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L, consignment, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(1), null);
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .willReturn(List.of(auction));
    stubEmptyAssemblyDependencies();

    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, null, 5, null, null, null, null, null);

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        auctionService.searchAuctions(null, request);

    // then
    assertThat(response.items().get(0).remainingSeconds()).isNull();
    assertThat(response.items().get(0).currentPrice()).isNull();
  }

  @Test
  void 썸네일은_배치조회_결과중_첫_이미지를_사용한다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L, consignment, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(1), null);
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .willReturn(List.of(auction));
    given(certificateManageService.getCertificatesByConsignmentId(any())).willReturn(Map.of());
    given(
            consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
                any()))
        .willReturn(
            List.of(
                createConsignmentImage(consignment, 1, "https://image.example.com/front.png"),
                createConsignmentImage(consignment, 2, "https://image.example.com/back.png")));
    given(watchRepository.findWatchSummariesByAuctionIds(any(), any())).willReturn(Map.of());

    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, null, 5, null, null, null, null, null);

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        auctionService.searchAuctions(null, request);

    // then
    assertThat(response.items().get(0).thumbnailUrl())
        .isEqualTo("https://image.example.com/front.png");
  }

  @Test
  void 인증서가_없으면_grade가_null이다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L, consignment, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(1), null);
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .willReturn(List.of(auction));
    stubEmptyAssemblyDependencies();

    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, null, 5, null, null, null, null, null);

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        auctionService.searchAuctions(null, request);

    // then
    assertThat(response.items().get(0).grade()).isNull();
  }

  @Test
  void 인증서가_있으면_grade가_조합된다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L, consignment, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(1), null);
    given(
            auctionRepository.searchAuctions(
                any(), any(), any(), any(), anyInt(), any(), any(), any()))
        .willReturn(List.of(auction));
    given(certificateManageService.getCertificatesByConsignmentId(any()))
        .willReturn(
            Map.of(
                consignment.getConsignmentId(),
                createCertificate(consignment, CertificationBody.PSA, Grade.GEM_MINT)));
    given(
            consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
                any()))
        .willReturn(List.of());
    given(watchRepository.findWatchSummariesByAuctionIds(any(), any())).willReturn(Map.of());

    SearchAuctionsRequest request =
        new SearchAuctionsRequest(null, null, null, 5, null, null, null, null, null);

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        auctionService.searchAuctions(null, request);

    // then
    assertThat(response.items().get(0).grade()).isEqualTo("PSA 10");
  }

  @Test
  void 존재하는_경매를_조회하면_상세정보를_반환한다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L,
            consignment,
            AuctionStatus.ONGOING,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1));
    Certificate certificate = createCertificate(consignment, CertificationBody.PSA, Grade.GEM_MINT);
    ConsignmentImage front =
        createConsignmentImage(consignment, 1, "https://image.example.com/front.png");
    given(auctionRepository.findByIdWithConsignmentAndCard(1L)).willReturn(Optional.of(auction));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    given(consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment))
        .willReturn(List.of(front));
    given(watchRepository.findWatchSummariesByAuctionIds(9L, List.of(1L)))
        .willReturn(Map.of(1L, new WatchSummary(4L, true)));

    // when
    AuctionDetailResponse response = auctionService.getAuctionDetail(9L, 1L);

    // then
    assertThat(response.auctionId()).isEqualTo(1L);
    assertThat(response.consignmentId()).isEqualTo(100L);
    assertThat(response.grade()).isEqualTo("PSA 10");
    assertThat(response.cardState()).isEqualTo("Gem Mint");
    assertThat(response.sellerId()).isEqualTo(1L);
    assertThat(response.sellerNickname()).isEqualTo("닉네임");
    assertThat(response.thumbnailUrl()).isEqualTo("https://image.example.com/front.png");
    assertThat(response.watchCount()).isEqualTo(4L);
    assertThat(response.watched()).isTrue();
    assertThat(response.currentPrice()).isEqualTo(10000L);
    assertThat(response.nextMinBid()).isEqualTo(10500L);
    assertThat(response.recommendedBid()).isNull();
    assertThat(response.remainingSeconds()).isCloseTo(60 * 60L, Offset.offset(5L));
  }

  @Test
  void 낙찰된_경매를_낙찰자가_조회하면_myBidWon이_true다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.SOLD, null);
    Auction auction =
        createAuction(
            1L,
            consignment,
            AuctionStatus.WON,
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusHours(1));
    Certificate certificate = createCertificate(consignment, CertificationBody.PSA, Grade.GEM_MINT);
    Bid winningBid = Bid.create(auction, createMember(9L), 10000L);
    ReflectionTestUtils.setField(winningBid, "bidId", 50L);
    auction.updateWinningBid(50L, 10000L);
    given(auctionRepository.findByIdWithConsignmentAndCard(1L)).willReturn(Optional.of(auction));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    given(consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment))
        .willReturn(List.of());
    given(watchRepository.findWatchSummariesByAuctionIds(9L, List.of(1L))).willReturn(Map.of());
    given(bidRepository.findById(50L)).willReturn(Optional.of(winningBid));

    // when
    AuctionDetailResponse response = auctionService.getAuctionDetail(9L, 1L);

    // then
    assertThat(response.myBidWon()).isTrue();
  }

  @Test
  void 낙찰된_경매를_낙찰자가_아닌_회원이_조회하면_myBidWon이_false다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.SOLD, null);
    Auction auction =
        createAuction(
            1L,
            consignment,
            AuctionStatus.WON,
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusHours(1));
    Certificate certificate = createCertificate(consignment, CertificationBody.PSA, Grade.GEM_MINT);
    Bid winningBid = Bid.create(auction, createMember(9L), 10000L);
    ReflectionTestUtils.setField(winningBid, "bidId", 50L);
    auction.updateWinningBid(50L, 10000L);
    given(auctionRepository.findByIdWithConsignmentAndCard(1L)).willReturn(Optional.of(auction));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    given(consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment))
        .willReturn(List.of());
    given(watchRepository.findWatchSummariesByAuctionIds(42L, List.of(1L))).willReturn(Map.of());
    given(bidRepository.findById(50L)).willReturn(Optional.of(winningBid));

    // when
    AuctionDetailResponse response = auctionService.getAuctionDetail(42L, 1L);

    // then
    assertThat(response.myBidWon()).isFalse();
  }

  @Test
  void 낙찰된_경매를_비로그인_상태로_조회하면_myBidWon이_false다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.SOLD, null);
    Auction auction =
        createAuction(
            1L,
            consignment,
            AuctionStatus.WON,
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusHours(1));
    Certificate certificate = createCertificate(consignment, CertificationBody.PSA, Grade.GEM_MINT);
    given(auctionRepository.findByIdWithConsignmentAndCard(1L)).willReturn(Optional.of(auction));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    given(consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment))
        .willReturn(List.of());
    given(watchRepository.findWatchSummariesByAuctionIds(isNull(), eq(List.of(1L))))
        .willReturn(Map.of());

    // when
    AuctionDetailResponse response = auctionService.getAuctionDetail(null, 1L);

    // then
    assertThat(response.myBidWon()).isFalse();
  }

  @Test
  void 입찰이_있는_경매_상세를_조회하면_currentPrice와_nextMinBid가_반영된다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L,
            consignment,
            AuctionStatus.ONGOING,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1));
    auction.updateWinningBid(50L, 12000L);
    Certificate certificate = createCertificate(consignment, CertificationBody.PSA, Grade.GEM_MINT);
    given(auctionRepository.findByIdWithConsignmentAndCard(1L)).willReturn(Optional.of(auction));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    given(consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment))
        .willReturn(List.of());
    given(watchRepository.findWatchSummariesByAuctionIds(isNull(), eq(List.of(1L))))
        .willReturn(Map.of());

    // when
    AuctionDetailResponse response = auctionService.getAuctionDetail(null, 1L);

    // then
    assertThat(response.currentPrice()).isEqualTo(12000L);
    assertThat(response.nextMinBid()).isEqualTo(12500L);
  }

  @Test
  void 현재가와_최소_입찰_단위를_더했을_때_Long_범위를_넘으면_예외가_발생한다() {
    // given
    // startingPrice에 상한이 없어 이런 값도 등록 자체는 막히지 않는다. 조용히 음수로
    // 랩어라운드된 nextMinBid를 200으로 내려보내는 대신, addExact가 던지는 예외로
    // 드러나야 한다.
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(LocalDateTime.now().plusHours(1))
            .auctionStatus(AuctionStatus.ONGOING)
            .startingPrice(9_223_372_036_000_000_000L)
            .reservePrice(9_223_372_036_000_000_000L)
            .bidIncrement(461_168_601_800_000_000L)
            .build();
    ReflectionTestUtils.setField(auction, "auctionId", 1L);
    Certificate certificate = createCertificate(consignment, CertificationBody.PSA, Grade.GEM_MINT);
    given(auctionRepository.findByIdWithConsignmentAndCard(1L)).willReturn(Optional.of(auction));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    given(consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment))
        .willReturn(List.of());

    // when & then
    assertThatThrownBy(() -> auctionService.getAuctionDetail(null, 1L))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void 비로그인_사용자가_상세를_조회하면_watched가_false다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L, consignment, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(1), null);
    Certificate certificate = createCertificate(consignment, CertificationBody.PSA, Grade.MINT);
    given(auctionRepository.findByIdWithConsignmentAndCard(1L)).willReturn(Optional.of(auction));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    given(consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment))
        .willReturn(List.of());
    given(watchRepository.findWatchSummariesByAuctionIds(isNull(), eq(List.of(1L))))
        .willReturn(Map.of());

    // when
    AuctionDetailResponse response = auctionService.getAuctionDetail(null, 1L);

    // then
    assertThat(response.watched()).isFalse();
    assertThat(response.watchCount()).isEqualTo(0L);
    assertThat(response.thumbnailUrl()).isNull();
    assertThat(response.currentPrice()).isNull();
  }

  @Test
  void 존재하지_않는_경매를_조회하면_예외가_발생한다() {
    // given
    given(auctionRepository.findByIdWithConsignmentAndCard(999L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> auctionService.getAuctionDetail(null, 999L))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            e ->
                assertThat(((PickUpException) e).getMessage())
                    .isEqualTo(ExceptionCode.AUCTION_NOT_FOUND.getMessage()));
  }

  @Test
  void 인증서가_없는_경매를_조회하면_예외가_발생한다() {
    // given
    Consignment consignment = createConsignment(100L, 1L, ConsignmentStatus.IN_AUCTION, null);
    Auction auction =
        createAuction(
            1L, consignment, AuctionStatus.SCHEDULED, LocalDateTime.now().plusDays(1), null);
    given(auctionRepository.findByIdWithConsignmentAndCard(1L)).willReturn(Optional.of(auction));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> auctionService.getAuctionDetail(null, 1L))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            e ->
                assertThat(((PickUpException) e).getMessage())
                    .isEqualTo(ExceptionCode.CERTIFICATE_NOT_FOUND.getMessage()));
  }

  private void stubEmptyAssemblyDependencies() {
    given(certificateManageService.getCertificatesByConsignmentId(any())).willReturn(Map.of());
    given(
            consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
                any()))
        .willReturn(List.of());
    given(watchRepository.findWatchSummariesByAuctionIds(any(), any())).willReturn(Map.of());
  }

  private Consignment createConsignment(
      Long consignmentId, Long sellerMemberId, ConsignmentStatus status, Card card) {
    Consignment consignment =
        Consignment.builder()
            .card(card != null ? card : createCard(10L))
            .sellerMember(createMember(sellerMemberId))
            .status(status)
            .build();
    ReflectionTestUtils.setField(consignment, "consignmentId", consignmentId);
    return consignment;
  }

  private Card createCard(Long cardId) {
    Card card =
        Card.builder()
            .cardName("리자몽 1st Edition Holo")
            .cardNumber("4/102")
            .setName("Base Set")
            .language(Language.JAPANESE)
            .rarity(Rarity.RARE_HOLO)
            .imageUrl("https://image.example.com/card.png")
            .build();
    ReflectionTestUtils.setField(card, "cardId", cardId);
    return card;
  }

  private Member createMember(Long memberId) {
    Member member = Member.create("loginId", "password", "닉네임");
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }

  private Auction createAuction(
      Long auctionId,
      Consignment consignment,
      AuctionStatus status,
      LocalDateTime startedAt,
      LocalDateTime endedAt) {
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(startedAt)
            .endedAt(endedAt)
            .auctionStatus(status)
            .startingPrice(10000L)
            .reservePrice(15000L)
            .bidIncrement(500L)
            .build();
    ReflectionTestUtils.setField(auction, "auctionId", auctionId);
    ReflectionTestUtils.setField(auction, "createdAt", LocalDateTime.of(2026, 7, 1, 12, 0));
    return auction;
  }

  private ConsignmentImage createConsignmentImage(
      Consignment consignment, int imageOrder, String imageUrl) {
    return ConsignmentImage.builder()
        .consignment(consignment)
        .imageOrder(imageOrder)
        .objectKey(imageUrl)
        .build();
  }

  private Certificate createCertificate(
      Consignment consignment, CertificationBody certificationBody, Grade grade) {
    return Certificate.builder()
        .serialNumber("SERIAL-1")
        .consignment(consignment)
        .grade(grade)
        .certificationBody(certificationBody)
        .inspectedAt(LocalDate.of(2026, 1, 1))
        .build();
  }
}
