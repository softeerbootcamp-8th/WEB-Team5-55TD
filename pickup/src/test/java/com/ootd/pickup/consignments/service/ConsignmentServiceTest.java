package com.ootd.pickup.consignments.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.service.CardManageService;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.domain.Grade;
import com.ootd.pickup.consignments.dto.request.CertificateRequest;
import com.ootd.pickup.consignments.dto.request.ConsignmentImageRequest;
import com.ootd.pickup.consignments.dto.request.GetMyConsignmentsRequest;
import com.ootd.pickup.consignments.dto.request.ModifyConsignmentRequest;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.GetMyConsignmentsResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.repository.certificate.CertificateRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import com.ootd.pickup.consignments.repository.consignmentImage.ConsignmentImageRepository;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.service.MemberManageService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsignmentServiceTest {

  @Mock private CardManageService cardManageService;

  @Mock private ConsignmentRepository consignmentRepository;

  @Mock private CertificateRepository certificateRepository;

  @Mock private ConsignmentImageRepository consignmentImageRepository;

  @Mock private MemberManageService memberManageService;

  private ConsignmentService consignmentService;

  @BeforeEach
  void setUp() {
    consignmentService =
        new ConsignmentService(
            cardManageService,
            consignmentRepository,
            certificateRepository,
            consignmentImageRepository,
            memberManageService);
  }

  @Test
  void 유효한_요청으로_상품을_등록하면_상품_상세정보를_반환한다() {
    // given
    Long sellerMemberId = 1L;
    Long cardId = 10L;
    Card card = createCard(cardId);
    given(memberManageService.getMemberById(sellerMemberId))
        .willReturn(createMember(sellerMemberId, "피카츄"));
    given(cardManageService.getCardByCardId(cardId)).willReturn(card);
    given(consignmentRepository.save(any(Consignment.class)))
        .willAnswer(
            invocation -> {
              Consignment consignment = invocation.getArgument(0);
              ReflectionTestUtils.setField(consignment, "consignmentId", 100L);
              return consignment;
            });
    given(certificateRepository.save(any(Certificate.class)))
        .willAnswer(
            invocation -> {
              Certificate certificate = invocation.getArgument(0);
              ReflectionTestUtils.setField(certificate, "certificateId", 200L);
              return certificate;
            });

    RegisterConsignmentRequest request =
        new RegisterConsignmentRequest(
            cardId,
            "모서리에 약간의 마모",
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when
    RegisterConsignmentResponse response =
        consignmentService.registerConsignment(sellerMemberId, request);

    // then
    assertThat(response.consignmentId()).isEqualTo(100L);
    assertThat(response.sellerMemberId()).isEqualTo(sellerMemberId);
    assertThat(response.majorDefect()).isEqualTo("모서리에 약간의 마모");
    assertThat(response.status()).isEqualTo(ConsignmentStatus.REGISTERABLE);
    assertThat(response.card().cardId()).isEqualTo(cardId);
    assertThat(response.certificate().certificateId()).isEqualTo(200L);
    assertThat(response.certificate().serialNumber()).isEqualTo("PSA-84213907");
    assertThat(response.certificate().certificationBody()).isEqualTo(CertificationBody.PSA);
    assertThat(response.certificate().grade()).isEqualTo("10");
    assertThat(response.certificate().gradeCode()).isEqualTo("GEM_MINT");
    assertThat(response.certificate().inspectedAt()).isEqualTo(LocalDate.of(2026, 6, 30));

    ArgumentCaptor<List<ConsignmentImage>> imagesCaptor = ArgumentCaptor.forClass(List.class);
    then(consignmentImageRepository).should().saveAll(imagesCaptor.capture());
    assertThat(imagesCaptor.getValue())
        .extracting(ConsignmentImage::getImageOrder, ConsignmentImage::getImageUrl)
        .containsExactly(
            tuple(1, "https://image.example.com/front.png"),
            tuple(2, "https://image.example.com/back.png"));
  }

  @Test
  void 존재하지_않는_카드ID로_상품을_등록하면_예외가_발생한다() {
    // given
    Long sellerMemberId = 1L;
    Long notExistCardId = 999L;
    given(cardManageService.getCardByCardId(notExistCardId))
        .willThrow(new PickUpException(ExceptionCode.CARD_NOT_FOUND));

    RegisterConsignmentRequest request =
        new RegisterConsignmentRequest(
            notExistCardId,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(() -> consignmentService.registerConsignment(sellerMemberId, request))
        .isInstanceOf(PickUpException.class);
    then(consignmentRepository).shouldHaveNoInteractions();
  }

  @Test
  void 유효하지_않은_등급이면_예외가_발생한다() {
    // given
    Long sellerMemberId = 1L;
    Long cardId = 10L;
    Card card = createCard(cardId);
    given(cardManageService.getCardByCardId(cardId)).willReturn(card);

    RegisterConsignmentRequest request =
        new RegisterConsignmentRequest(
            cardId,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "S급", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(() -> consignmentService.registerConsignment(sellerMemberId, request))
        .isInstanceOf(PickUpException.class);
    then(consignmentRepository).shouldHaveNoInteractions();
    then(certificateRepository).shouldHaveNoInteractions();
  }

  @Test
  void 존재하지_않는_회원이_상품을_등록하면_예외가_발생한다() {
    // given
    Long notExistMemberId = 999L;
    given(memberManageService.getMemberById(notExistMemberId))
        .willThrow(new PickUpException(ExceptionCode.MEMBER_NOT_FOUND));

    RegisterConsignmentRequest request =
        new RegisterConsignmentRequest(
            10L,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(() -> consignmentService.registerConsignment(notExistMemberId, request))
        .isInstanceOf(PickUpException.class);
    then(cardManageService).shouldHaveNoInteractions();
    then(consignmentRepository).shouldHaveNoInteractions();
  }

  @Test
  void 이미_존재하는_인증서_일련번호로_등록하면_예외가_발생한다() {
    // given
    Long sellerMemberId = 1L;
    Long cardId = 10L;
    Card card = createCard(cardId);
    given(memberManageService.getMemberById(sellerMemberId))
        .willReturn(createMember(sellerMemberId, "피카츄"));
    given(cardManageService.getCardByCardId(cardId)).willReturn(card);
    given(consignmentRepository.save(any(Consignment.class)))
        .willAnswer(
            invocation -> {
              Consignment consignment = invocation.getArgument(0);
              ReflectionTestUtils.setField(consignment, "consignmentId", 100L);
              return consignment;
            });
    given(certificateRepository.save(any(Certificate.class)))
        .willThrow(new DataIntegrityViolationException("duplicate serial number"));

    RegisterConsignmentRequest request =
        new RegisterConsignmentRequest(
            cardId,
            "모서리에 약간의 마모",
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(() -> consignmentService.registerConsignment(sellerMemberId, request))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.CERTIFICATE_SERIAL_NUMBER_ALREADY_EXISTS.getMessage());
    then(consignmentImageRepository).shouldHaveNoInteractions();
  }

  @Test
  void 존재하는_상품ID로_조회하면_상품_상세정보를_반환한다() {
    // given
    Long consignmentId = 100L;
    Card card = createCard(10L);
    Consignment consignment =
        createConsignment(consignmentId, card, ConsignmentStatus.REGISTERABLE);
    Certificate certificate = createCertificate(200L, consignment);
    List<ConsignmentImage> images =
        List.of(
            createConsignmentImage(1L, consignment, 1, "https://image.example.com/front.png"),
            createConsignmentImage(2L, consignment, 2, "https://image.example.com/back.png"));
    given(consignmentRepository.findConsignmentById(consignmentId))
        .willReturn(Optional.of(consignment));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    given(consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment))
        .willReturn(images);

    // when
    GetConsignmentDetailResponse response = consignmentService.getConsignment(consignmentId);

    // then
    assertThat(response.consignmentId()).isEqualTo(consignmentId);
    assertThat(response.card().cardId()).isEqualTo(10L);
    assertThat(response.sellerMemberNickname()).isEqualTo("피카츄");
    assertThat(response.status()).isEqualTo(ConsignmentStatus.REGISTERABLE);
    assertThat(response.certificate().certificateId()).isEqualTo(200L);
    assertThat(response.auctionRegistered()).isFalse();
    assertThat(response.images())
        .extracting(
            imageResponse -> imageResponse.imageOrder(), imageResponse -> imageResponse.imageUrl())
        .containsExactly(
            tuple(1, "https://image.example.com/front.png"),
            tuple(2, "https://image.example.com/back.png"));
  }

  @Test
  void REGISTERABLE이_아닌_상품을_조회하면_경매등록상태가_true다() {
    // given
    Long consignmentId = 100L;
    Card card = createCard(10L);
    Consignment consignment =
        createConsignment(consignmentId, card, ConsignmentStatus.AUCTION_ONGOING);
    Certificate certificate = createCertificate(200L, consignment);
    given(consignmentRepository.findConsignmentById(consignmentId))
        .willReturn(Optional.of(consignment));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    given(consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment))
        .willReturn(List.of());

    // when
    GetConsignmentDetailResponse response = consignmentService.getConsignment(consignmentId);

    // then
    assertThat(response.auctionRegistered()).isTrue();
  }

  @Test
  void 존재하지_않는_상품ID로_조회하면_예외가_발생한다() {
    // given
    Long notExistConsignmentId = 999L;
    given(consignmentRepository.findConsignmentById(notExistConsignmentId))
        .willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> consignmentService.getConsignment(notExistConsignmentId))
        .isInstanceOf(PickUpException.class);
    then(certificateRepository).shouldHaveNoInteractions();
    then(consignmentImageRepository).shouldHaveNoInteractions();
  }

  @Test
  void 상품에_연결된_인증서를_찾을_수_없으면_예외가_발생한다() {
    // given
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.REGISTERABLE);
    given(consignmentRepository.findConsignmentById(consignmentId))
        .willReturn(Optional.of(consignment));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> consignmentService.getConsignment(consignmentId))
        .isInstanceOf(PickUpException.class);
    then(consignmentImageRepository).shouldHaveNoInteractions();
  }

  @Test
  void 소유자가_REGISTERABLE_상태의_상품을_수정하면_수정된_상세정보를_반환한다() {
    // given
    Long sellerMemberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.REGISTERABLE);
    Certificate certificate = createCertificate(200L, consignment);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    given(consignmentImageRepository.saveAll(anyList()))
        .willAnswer(invocation -> invocation.getArgument(0));

    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            "새로운 흠집 설명",
            new CertificateRequest("PSA-99999999", "PSA", "9", LocalDate.of(2026, 7, 1)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/new-front.png"),
                new ConsignmentImageRequest("https://image.example.com/new-back.png")));

    // when
    GetConsignmentDetailResponse response =
        consignmentService.modifyConsignment(consignmentId, sellerMemberId, request);

    // then
    assertThat(response.consignmentId()).isEqualTo(consignmentId);
    assertThat(response.majorDefect()).isEqualTo("새로운 흠집 설명");
    assertThat(response.certificate().certificateId()).isEqualTo(200L);
    assertThat(response.certificate().serialNumber()).isEqualTo("PSA-99999999");
    assertThat(response.certificate().grade()).isEqualTo("9");
    then(certificateRepository).should(never()).save(any());
    then(consignmentImageRepository).should().deleteAllByConsignment(consignment);

    ArgumentCaptor<List<ConsignmentImage>> imagesCaptor = ArgumentCaptor.forClass(List.class);
    then(consignmentImageRepository).should().saveAll(imagesCaptor.capture());
    assertThat(imagesCaptor.getValue())
        .extracting(ConsignmentImage::getImageOrder, ConsignmentImage::getImageUrl)
        .containsExactly(
            tuple(1, "https://image.example.com/new-front.png"),
            tuple(2, "https://image.example.com/new-back.png"));
  }

  @Test
  void PASSED_상태의_상품도_수정할_수_있다() {
    // given
    Long sellerMemberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.PASSED);
    Certificate certificate = createCertificate(200L, consignment);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    given(consignmentImageRepository.saveAll(anyList()))
        .willAnswer(invocation -> invocation.getArgument(0));

    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when
    GetConsignmentDetailResponse response =
        consignmentService.modifyConsignment(consignmentId, sellerMemberId, request);

    // then
    assertThat(response.status()).isEqualTo(ConsignmentStatus.PASSED);
  }

  @Test
  void 존재하지_않는_상품을_수정하면_예외가_발생한다() {
    // given
    Long notExistConsignmentId = 999L;
    given(consignmentRepository.findByIdForUpdate(notExistConsignmentId))
        .willReturn(Optional.empty());

    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(
            () -> consignmentService.modifyConsignment(notExistConsignmentId, 1L, request))
        .isInstanceOf(PickUpException.class);
    then(certificateRepository).shouldHaveNoInteractions();
  }

  @Test
  void 본인이_등록한_상품이_아니면_예외가_발생한다() {
    // given
    Long consignmentId = 100L;
    Long otherMemberId = 999L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.REGISTERABLE);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));

    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(
            () -> consignmentService.modifyConsignment(consignmentId, otherMemberId, request))
        .isInstanceOf(PickUpException.class);
    then(certificateRepository).shouldHaveNoInteractions();
    then(consignmentImageRepository).shouldHaveNoInteractions();
  }

  @Test
  void 경매_진행중인_상품을_수정하면_예외가_발생한다() {
    // given
    Long sellerMemberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.AUCTION_ONGOING);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));

    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(
            () -> consignmentService.modifyConsignment(consignmentId, sellerMemberId, request))
        .isInstanceOf(PickUpException.class);
    then(certificateRepository).shouldHaveNoInteractions();
  }

  @Test
  void 유효하지_않은_등급으로_수정하면_예외가_발생한다() {
    // given
    Long sellerMemberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.REGISTERABLE);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));

    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            null,
            new CertificateRequest("PSA-84213907", "PSA", "S급", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(
            () -> consignmentService.modifyConsignment(consignmentId, sellerMemberId, request))
        .isInstanceOf(PickUpException.class);
    then(certificateRepository).shouldHaveNoInteractions();
  }

  @Test
  void 유효하지_않은_감정기관으로_수정하면_예외가_발생한다() {
    // given
    Long sellerMemberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.REGISTERABLE);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));

    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            null,
            new CertificateRequest("PSA-84213907", "GIA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(
            () -> consignmentService.modifyConsignment(consignmentId, sellerMemberId, request))
        .isInstanceOf(PickUpException.class);
    then(certificateRepository).shouldHaveNoInteractions();
  }

  @Test
  void 상품에_연결된_인증서를_찾을_수_없으면_수정시_예외가_발생한다() {
    // given
    Long sellerMemberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.REGISTERABLE);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.empty());

    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(
            () -> consignmentService.modifyConsignment(consignmentId, sellerMemberId, request))
        .isInstanceOf(PickUpException.class);
    then(consignmentImageRepository).shouldHaveNoInteractions();
  }

  @Test
  void 다른_상품이_사용중인_일련번호로_수정하면_예외가_발생한다() {
    // given
    Long sellerMemberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.REGISTERABLE);
    Certificate certificate = createCertificate(200L, consignment);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));
    given(certificateRepository.findCertificateByConsignment(consignment))
        .willReturn(Optional.of(certificate));
    willThrow(new DataIntegrityViolationException("duplicate serial number"))
        .given(certificateRepository)
        .flush();

    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            null,
            new CertificateRequest("OTHER-SERIAL-NUMBER", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(
            () -> consignmentService.modifyConsignment(consignmentId, sellerMemberId, request))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.CERTIFICATE_SERIAL_NUMBER_ALREADY_EXISTS.getMessage());
    then(consignmentImageRepository).shouldHaveNoInteractions();
  }

  @Test
  void 유효한_요청으로_내_상품_목록을_조회하면_상품_목록을_반환한다() {
    // given
    Long sellerMemberId = 1L;
    Card card = createCard(10L);
    Consignment consignment = createConsignment(100L, card, ConsignmentStatus.REGISTERABLE);
    Certificate certificate = createCertificate(200L, consignment);
    GetMyConsignmentsRequest request = new GetMyConsignmentsRequest("REGISTERABLE", null, 20);
    given(
            consignmentRepository.searchMyConsignments(
                sellerMemberId, ConsignmentStatus.REGISTERABLE, null, 21))
        .willReturn(List.of(consignment));
    given(certificateRepository.findAllByConsignmentIn(List.of(consignment)))
        .willReturn(List.of(certificate));

    // when
    CursorPageResponse<GetMyConsignmentsResponse, Long> response =
        consignmentService.getMyConsignments(sellerMemberId, request);

    // then
    assertThat(response.hasNext()).isFalse();
    assertThat(response.cursor()).isNull();
    assertThat(response.items()).hasSize(1);
    GetMyConsignmentsResponse item = response.items().get(0);
    assertThat(item.consignmentId()).isEqualTo(100L);
    assertThat(item.sellerMemberId()).isEqualTo(sellerMemberId);
    assertThat(item.card().cardId()).isEqualTo(10L);
    assertThat(item.status()).isEqualTo(ConsignmentStatus.REGISTERABLE);
    assertThat(item.certificate().certificateId()).isEqualTo(200L);
  }

  @Test
  void 조회_결과가_페이지_크기보다_많으면_hasNext가_true이고_다음_커서를_반환한다() {
    // given
    Long sellerMemberId = 1L;
    Consignment first = createConsignment(102L, createCard(10L), ConsignmentStatus.REGISTERABLE);
    Consignment second = createConsignment(101L, createCard(11L), ConsignmentStatus.REGISTERABLE);
    Consignment extra = createConsignment(100L, createCard(12L), ConsignmentStatus.REGISTERABLE);
    GetMyConsignmentsRequest request = new GetMyConsignmentsRequest("REGISTERABLE", null, 2);
    given(
            consignmentRepository.searchMyConsignments(
                sellerMemberId, ConsignmentStatus.REGISTERABLE, null, 3))
        .willReturn(List.of(first, second, extra));
    given(certificateRepository.findAllByConsignmentIn(List.of(first, second)))
        .willReturn(List.of(createCertificate(200L, first), createCertificate(201L, second)));

    // when
    CursorPageResponse<GetMyConsignmentsResponse, Long> response =
        consignmentService.getMyConsignments(sellerMemberId, request);

    // then
    assertThat(response.hasNext()).isTrue();
    assertThat(response.cursor()).isEqualTo(101L);
    assertThat(response.items())
        .extracting(GetMyConsignmentsResponse::consignmentId)
        .containsExactly(102L, 101L);
  }

  @Test
  void 유효하지_않은_status로_내_상품_목록을_조회하면_예외가_발생한다() {
    // given
    GetMyConsignmentsRequest request = new GetMyConsignmentsRequest("존재하지않는상태", null, 20);

    // when & then
    assertThatThrownBy(() -> consignmentService.getMyConsignments(1L, request))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.INVALID_CONSIGNMENT_STATUS.getMessage());
    then(consignmentRepository).shouldHaveNoInteractions();
  }

  @Test
  void size가_유효하지_않으면_내_상품_목록_조회시_예외가_발생한다() {
    // given
    GetMyConsignmentsRequest request = new GetMyConsignmentsRequest("REGISTERABLE", null, 0);

    // when & then
    assertThatThrownBy(() -> consignmentService.getMyConsignments(1L, request))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.INVALID_PAGE_SIZE.getMessage());
    then(consignmentRepository).shouldHaveNoInteractions();
  }

  @Test
  void 소유자가_REGISTERABLE_상태의_상품을_삭제하면_정상적으로_삭제된다() {
    // given
    Long sellerMemberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.REGISTERABLE);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));

    // when
    consignmentService.deleteConsignment(consignmentId, sellerMemberId);

    // then
    then(certificateRepository).should().deleteByConsignment(consignment);
    then(consignmentImageRepository).should().deleteAllByConsignment(consignment);
    then(consignmentRepository).should().deleteById(consignmentId);
  }

  @Test
  void PASSED_상태의_상품도_삭제할_수_있다() {
    // given
    Long sellerMemberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.PASSED);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));

    // when
    consignmentService.deleteConsignment(consignmentId, sellerMemberId);

    // then
    then(certificateRepository).should().deleteByConsignment(consignment);
    then(consignmentImageRepository).should().deleteAllByConsignment(consignment);
    then(consignmentRepository).should().deleteById(consignmentId);
  }

  @Test
  void 존재하지_않는_상품을_삭제하면_예외가_발생한다() {
    // given
    Long notExistConsignmentId = 999L;
    given(consignmentRepository.findByIdForUpdate(notExistConsignmentId))
        .willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> consignmentService.deleteConsignment(notExistConsignmentId, 1L))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.CONSIGNMENT_NOT_FOUND.getMessage());
    then(certificateRepository).shouldHaveNoInteractions();
    then(consignmentImageRepository).shouldHaveNoInteractions();
    then(consignmentRepository).should(never()).deleteById(any());
  }

  @Test
  void 본인이_등록한_상품이_아니면_삭제시_예외가_발생한다() {
    // given
    Long consignmentId = 100L;
    Long otherMemberId = 999L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.REGISTERABLE);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));

    // when & then
    assertThatThrownBy(() -> consignmentService.deleteConsignment(consignmentId, otherMemberId))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.CONSIGNMENT_DELETE_OWNER_MISMATCH.getMessage());
    then(certificateRepository).shouldHaveNoInteractions();
    then(consignmentImageRepository).shouldHaveNoInteractions();
    then(consignmentRepository).should(never()).deleteById(any());
  }

  @Test
  void 경매_진행중인_상품을_삭제하면_예외가_발생한다() {
    // given
    Long sellerMemberId = 1L;
    Long consignmentId = 100L;
    Consignment consignment =
        createConsignment(consignmentId, createCard(10L), ConsignmentStatus.AUCTION_ONGOING);
    given(consignmentRepository.findByIdForUpdate(consignmentId))
        .willReturn(Optional.of(consignment));

    // when & then
    assertThatThrownBy(() -> consignmentService.deleteConsignment(consignmentId, sellerMemberId))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.CONSIGNMENT_NOT_DELETABLE.getMessage());
    then(certificateRepository).shouldHaveNoInteractions();
    then(consignmentImageRepository).shouldHaveNoInteractions();
    then(consignmentRepository).should(never()).deleteById(any());
  }

  private Card createCard(Long cardId) {
    Card card =
        Card.builder()
            .cardName("리자몽 1st Edition Holo")
            .cardNumber("4/102")
            .setName("Base Set")
            .language(Language.JAPANESE)
            .rarity(Rarity.MINT)
            .imageUrl("https://image.example.com/card.png")
            .build();
    ReflectionTestUtils.setField(card, "cardId", cardId);
    return card;
  }

  private Consignment createConsignment(Long consignmentId, Card card, ConsignmentStatus status) {
    Consignment consignment =
        Consignment.builder()
            .card(card)
            .sellerMember(createMember(1L, "피카츄"))
            .majorDefect(null)
            .status(status)
            .build();
    ReflectionTestUtils.setField(consignment, "consignmentId", consignmentId);
    return consignment;
  }

  private Member createMember(Long memberId, String nickname) {
    Member member = Member.create("loginId", "password", nickname);
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }

  private Certificate createCertificate(Long certificateId, Consignment consignment) {
    Certificate certificate =
        Certificate.builder()
            .consignment(consignment)
            .serialNumber("PSA-84213907")
            .certificationBody(CertificationBody.PSA)
            .grade(Grade.GEM_MINT)
            .inspectedAt(LocalDate.of(2026, 6, 30))
            .build();
    ReflectionTestUtils.setField(certificate, "certificateId", certificateId);
    return certificate;
  }

  private ConsignmentImage createConsignmentImage(
      Long consignmentImageId, Consignment consignment, int imageOrder, String imageUrl) {
    ConsignmentImage consignmentImage =
        ConsignmentImage.builder()
            .consignment(consignment)
            .imageOrder(imageOrder)
            .imageUrl(imageUrl)
            .build();
    ReflectionTestUtils.setField(consignmentImage, "consignmentImageId", consignmentImageId);
    return consignmentImage;
  }
}
