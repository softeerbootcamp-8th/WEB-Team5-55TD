package com.ootd.pickup.consignments.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.repository.certificate.CertificateRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import com.ootd.pickup.consignments.repository.consignmentImage.ConsignmentImageRepository;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;

@ExtendWith(MockitoExtension.class)
class ConsignmentServiceTest {

    @Mock
    private CardManageService cardManageService;

    @Mock
    private ConsignmentRepository consignmentRepository;

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private ConsignmentImageRepository consignmentImageRepository;

    private ConsignmentService consignmentService;

    @BeforeEach
    void setUp() {
        consignmentService = new ConsignmentService(
            cardManageService, consignmentRepository, certificateRepository, consignmentImageRepository
        );
    }

    @Test
    void 유효한_요청으로_상품을_등록하면_상품_상세정보를_반환한다() {
        // given
        Long sellerMemberId = 1L;
        Long cardId = 10L;
        Card card = createCard(cardId);
        given(cardManageService.getCardByCardId(cardId)).willReturn(card);
        given(consignmentRepository.save(any(Consignment.class))).willAnswer(invocation -> {
            Consignment consignment = invocation.getArgument(0);
            ReflectionTestUtils.setField(consignment, "consignmentId", 100L);
            return consignment;
        });
        given(certificateRepository.save(any(Certificate.class))).willAnswer(invocation -> {
            Certificate certificate = invocation.getArgument(0);
            ReflectionTestUtils.setField(certificate, "certificateId", 200L);
            return certificate;
        });

        RegisterConsignmentRequest request = new RegisterConsignmentRequest(
            cardId,
            sellerMemberId,
            "모서리에 약간의 마모",
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")
            )
        );

        // when
        RegisterConsignmentResponse response = consignmentService.registerConsignment(sellerMemberId, request);

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
                tuple(2, "https://image.example.com/back.png")
            );
    }

    @Test
    void 존재하지_않는_카드ID로_상품을_등록하면_예외가_발생한다() {
        // given
        Long sellerMemberId = 1L;
        Long notExistCardId = 999L;
        given(cardManageService.getCardByCardId(notExistCardId))
            .willThrow(new PickUpException(ExceptionCode.CARD_NOT_FOUND));

        RegisterConsignmentRequest request = new RegisterConsignmentRequest(
            notExistCardId,
            sellerMemberId,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")
            )
        );

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

        RegisterConsignmentRequest request = new RegisterConsignmentRequest(
            cardId,
            sellerMemberId,
            null,
            new CertificateRequest("PSA-84213907", "PSA", "S급", LocalDate.of(2026, 6, 30)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")
            )
        );

        // when & then
        assertThatThrownBy(() -> consignmentService.registerConsignment(sellerMemberId, request))
            .isInstanceOf(PickUpException.class);
        then(consignmentRepository).shouldHaveNoInteractions();
        then(certificateRepository).shouldHaveNoInteractions();
    }

    @Test
    void 존재하는_상품ID로_조회하면_상품_상세정보를_반환한다() {
        // given
        Long consignmentId = 100L;
        Card card = createCard(10L);
        Consignment consignment = createConsignment(consignmentId, card, ConsignmentStatus.REGISTERABLE);
        Certificate certificate = createCertificate(200L, consignment);
        List<ConsignmentImage> images = List.of(
            createConsignmentImage(1L, consignment, 1, "https://image.example.com/front.png"),
            createConsignmentImage(2L, consignment, 2, "https://image.example.com/back.png")
        );
        given(consignmentRepository.findConsignmentById(consignmentId)).willReturn(Optional.of(consignment));
        given(certificateRepository.findCertificateByConsignment(consignment)).willReturn(Optional.of(certificate));
        given(consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment)).willReturn(images);

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
            .extracting(imageResponse -> imageResponse.imageOrder(), imageResponse -> imageResponse.imageUrl())
            .containsExactly(
                tuple(1, "https://image.example.com/front.png"),
                tuple(2, "https://image.example.com/back.png")
            );
    }

    @Test
    void REGISTERABLE이_아닌_상품을_조회하면_경매등록상태가_true다() {
        // given
        Long consignmentId = 100L;
        Card card = createCard(10L);
        Consignment consignment = createConsignment(consignmentId, card, ConsignmentStatus.AUCTION_ONGOING);
        Certificate certificate = createCertificate(200L, consignment);
        given(consignmentRepository.findConsignmentById(consignmentId)).willReturn(Optional.of(consignment));
        given(certificateRepository.findCertificateByConsignment(consignment)).willReturn(Optional.of(certificate));
        given(consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment)).willReturn(List.of());

        // when
        GetConsignmentDetailResponse response = consignmentService.getConsignment(consignmentId);

        // then
        assertThat(response.auctionRegistered()).isTrue();
    }

    @Test
    void 존재하지_않는_상품ID로_조회하면_예외가_발생한다() {
        // given
        Long notExistConsignmentId = 999L;
        given(consignmentRepository.findConsignmentById(notExistConsignmentId)).willReturn(Optional.empty());

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
        Consignment consignment = createConsignment(consignmentId, createCard(10L), ConsignmentStatus.REGISTERABLE);
        given(consignmentRepository.findConsignmentById(consignmentId)).willReturn(Optional.of(consignment));
        given(certificateRepository.findCertificateByConsignment(consignment)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> consignmentService.getConsignment(consignmentId))
            .isInstanceOf(PickUpException.class);
        then(consignmentImageRepository).shouldHaveNoInteractions();
    }

    private Card createCard(Long cardId) {
        Card card = Card.builder()
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
        Consignment consignment = Consignment.builder()
            .card(card)
            .sellerMemberId(1L)
            .majorDefect(null)
            .status(status)
            .build();
        ReflectionTestUtils.setField(consignment, "consignmentId", consignmentId);
        return consignment;
    }

    private Certificate createCertificate(Long certificateId, Consignment consignment) {
        Certificate certificate = Certificate.builder()
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
        Long consignmentImageId,
        Consignment consignment,
        int imageOrder,
        String imageUrl
    ) {
        ConsignmentImage consignmentImage = ConsignmentImage.builder()
            .consignment(consignment)
            .imageOrder(imageOrder)
            .imageUrl(imageUrl)
            .build();
        ReflectionTestUtils.setField(consignmentImage, "consignmentImageId", consignmentImageId);
        return consignmentImage;
    }
}
