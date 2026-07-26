package com.ootd.pickup.consignments.service;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.service.CardManageService;
import com.ootd.pickup.consignments.domain.*;
import com.ootd.pickup.consignments.dto.request.CertificateRequest;
import com.ootd.pickup.consignments.dto.request.ConsignmentImageRequest;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.repository.CertificateRepository;
import com.ootd.pickup.consignments.repository.ConsignmentImageRepository;
import com.ootd.pickup.consignments.repository.ConsignmentRepository;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

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
                        new ConsignmentImageRequest(1, "https://image.example.com/front.png"),
                        new ConsignmentImageRequest(2, "https://image.example.com/back.png")
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
                        new ConsignmentImageRequest(1, "https://image.example.com/front.png"),
                        new ConsignmentImageRequest(2, "https://image.example.com/back.png")
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
                        new ConsignmentImageRequest(1, "https://image.example.com/front.png"),
                        new ConsignmentImageRequest(2, "https://image.example.com/back.png")
                )
        );

        // when & then
        assertThatThrownBy(() -> consignmentService.registerConsignment(sellerMemberId, request))
                .isInstanceOf(PickUpException.class);
        then(consignmentRepository).shouldHaveNoInteractions();
        then(certificateRepository).shouldHaveNoInteractions();
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
}
