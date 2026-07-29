package com.ootd.pickup.consignments.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.service.CardManageService;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.domain.Grade;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.repository.certificate.CertificateRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import com.ootd.pickup.consignments.repository.consignmentImage.ConsignmentImageRepository;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.service.MemberManageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsignmentService {

  private final CardManageService cardManageService;
  private final ConsignmentRepository consignmentRepository;
  private final CertificateRepository certificateRepository;
  private final ConsignmentImageRepository consignmentImageRepository;
  private final MemberManageService memberManageService;

  @Transactional
  public RegisterConsignmentResponse registerConsignment(
      Long sellerMemberId, RegisterConsignmentRequest request) {
    memberManageService.validateMemberExists(sellerMemberId);

    Card card = cardManageService.getCardByCardId(request.cardId());
    // Consignment를 저장하기 전에 인증서 값부터 검증해 불필요한 INSERT를 막는다.
    Grade.from(request.certificate().grade());
    CertificationBody.from(request.certificate().certificationBody());

    Consignment consignment =
        consignmentRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMemberId(sellerMemberId)
                .majorDefect(request.majorDefect())
                .status(ConsignmentStatus.REGISTERABLE)
                .build());

    Certificate certificate =
        certificateRepository.save(request.certificate().toEntity(consignment));

    consignmentImageRepository.saveAll(request.toConsignmentImages(consignment));

    return RegisterConsignmentResponse.of(consignment, certificate);
  }

  public GetConsignmentDetailResponse getConsignment(Long consignmentId) {
    Consignment consignment =
        consignmentRepository
            .findConsignmentById(consignmentId)
            .orElseThrow(() -> new PickUpException(CONSIGNMENT_NOT_FOUND));

    Certificate certificate =
        certificateRepository
            .findCertificateByConsignment(consignment)
            .orElseThrow(() -> new PickUpException(CERTIFICATE_NOT_FOUND));

    List<ConsignmentImage> images =
        consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment);

    // TODO: 회원 도메인 구현 후 실제 판매자 닉네임으로 교체
    String sellerMemberNickname = "피카츄";

    return GetConsignmentDetailResponse.of(consignment, certificate, images, sellerMemberNickname);
  }
}
