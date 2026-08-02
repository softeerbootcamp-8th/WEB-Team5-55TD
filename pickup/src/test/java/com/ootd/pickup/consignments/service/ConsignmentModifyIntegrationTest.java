package com.ootd.pickup.consignments.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.domain.Grade;
import com.ootd.pickup.consignments.dto.request.CertificateRequest;
import com.ootd.pickup.consignments.dto.request.ConsignmentImageRequest;
import com.ootd.pickup.consignments.dto.request.ModifyConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.repository.certificate.CertificateJpaRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.domain.ImagePurpose;
import com.ootd.pickup.images.service.ImageService;
import com.ootd.pickup.images.service.ImageService.FinalizedImage;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConsignmentModifyIntegrationTest {

  @Autowired private ConsignmentService consignmentService;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private CertificateJpaRepository certificateJpaRepository;

  @Autowired private EntityManager entityManager;

  @MockitoBean private ImageService imageService;

  @BeforeEach
  void setUpImageService() {
    given(imageService.finalizeImages(anyLong(), eq(ImagePurpose.CONSIGNMENT), anyList()))
        .willAnswer(
            invocation ->
                ((List<String>) invocation.getArgument(2))
                    .stream()
                        .map(
                            key ->
                                new FinalizedImage(
                                    key,
                                    "media/consignments/1/"
                                        + Integer.toUnsignedString(key.hashCode())
                                        + ".jpg"))
                        .toList());
  }

  @Test
  void 기존_인증서와_동일한_일련번호로_수정해도_유니크_제약_위반이_발생하지_않는다() {
    // given
    Member seller = memberJpaRepository.save(Member.create("loginId", "password", "피카츄"));
    Card card = cardJpaRepository.save(createCard());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(seller)
                .majorDefect(null)
                .status(ConsignmentStatus.REGISTERABLE)
                .build());
    certificateJpaRepository.save(
        Certificate.builder()
            .consignment(consignment)
            .serialNumber("PSA-84213907")
            .certificationBody(CertificationBody.PSA)
            .grade(Grade.GEM_MINT)
            .inspectedAt(LocalDate.of(2026, 6, 30))
            .build());
    entityManager.flush();

    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            "동일한 일련번호로 재수정",
            new CertificateRequest("PSA-84213907", "PSA", "10", LocalDate.of(2026, 7, 1)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatCode(
            () -> {
              GetConsignmentDetailResponse response =
                  consignmentService.modifyConsignment(
                      consignment.getConsignmentId(), seller.getMemberId(), request);
              entityManager.flush();
              assertThat(response.majorDefect()).isEqualTo("동일한 일련번호로 재수정");
              assertThat(response.certificate().serialNumber()).isEqualTo("PSA-84213907");
              assertThat(response.certificate().grade()).isEqualTo("10");
            })
        .doesNotThrowAnyException();
  }

  @Test
  void 다른_상품이_사용중인_일련번호로_수정하면_409로_변환된다() {
    // given
    Member seller = memberJpaRepository.save(Member.create("loginId", "password", "피카츄"));
    Card card = cardJpaRepository.save(createCard());

    Consignment otherConsignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(seller)
                .majorDefect(null)
                .status(ConsignmentStatus.REGISTERABLE)
                .build());
    certificateJpaRepository.save(
        Certificate.builder()
            .consignment(otherConsignment)
            .serialNumber("PSA-OTHER-11111111")
            .certificationBody(CertificationBody.PSA)
            .grade(Grade.GEM_MINT)
            .inspectedAt(LocalDate.of(2026, 6, 30))
            .build());

    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(seller)
                .majorDefect(null)
                .status(ConsignmentStatus.REGISTERABLE)
                .build());
    certificateJpaRepository.save(
        Certificate.builder()
            .consignment(consignment)
            .serialNumber("PSA-84213907")
            .certificationBody(CertificationBody.PSA)
            .grade(Grade.GEM_MINT)
            .inspectedAt(LocalDate.of(2026, 6, 30))
            .build());
    entityManager.flush();

    ModifyConsignmentRequest request =
        new ModifyConsignmentRequest(
            null,
            new CertificateRequest("PSA-OTHER-11111111", "PSA", "10", LocalDate.of(2026, 7, 1)),
            List.of(
                new ConsignmentImageRequest("https://image.example.com/front.png"),
                new ConsignmentImageRequest("https://image.example.com/back.png")));

    // when & then
    assertThatThrownBy(
            () -> {
              consignmentService.modifyConsignment(
                  consignment.getConsignmentId(), seller.getMemberId(), request);
              entityManager.flush();
            })
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.CERTIFICATE_SERIAL_NUMBER_ALREADY_EXISTS.getMessage());
  }

  private Card createCard() {
    return Card.builder()
        .cardName("리자몽 1st Edition Holo")
        .cardNumber("4/102")
        .setName("Base Set")
        .language(Language.JAPANESE)
        .rarity(Rarity.MINT)
        .imageUrl("https://image.example.com/card.png")
        .build();
  }
}
