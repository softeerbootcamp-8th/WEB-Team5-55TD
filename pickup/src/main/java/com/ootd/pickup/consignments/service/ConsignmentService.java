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
import com.ootd.pickup.consignments.dto.request.ModifyConsignmentRequest;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.repository.certificate.CertificateRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import com.ootd.pickup.consignments.repository.consignmentImage.ConsignmentImageRepository;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.service.MemberManageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    Member sellerMember = memberManageService.getMemberById(sellerMemberId);

    Card card = cardManageService.getCardByCardId(request.cardId());
    // Consignment를 저장하기 전에 인증서 값부터 검증해 불필요한 INSERT를 막는다.
    Grade.from(request.certificate().grade());
    CertificationBody.from(request.certificate().certificationBody());

    Consignment consignment =
        consignmentRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(sellerMember)
                .majorDefect(request.majorDefect())
                .status(ConsignmentStatus.REGISTERABLE)
                .build());

    Certificate certificate;
    try {
      certificate = certificateRepository.save(request.certificate().toEntity(consignment));
    } catch (DataIntegrityViolationException exception) {
      throw new PickUpException(CERTIFICATE_SERIAL_NUMBER_ALREADY_EXISTS);
    }

    consignmentImageRepository.saveAll(request.toConsignmentImages(consignment));

    return RegisterConsignmentResponse.of(consignment, certificate);
  }

  public GetConsignmentDetailResponse getConsignment(Long consignmentId) {
    Consignment consignment =
        consignmentRepository
            .findConsignmentById(consignmentId)
            .orElseThrow(() -> new PickUpException(CONSIGNMENT_NOT_FOUND));

    Certificate certificate = getCertificate(consignment);

    List<ConsignmentImage> images =
        consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment);

    return GetConsignmentDetailResponse.of(
        consignment, certificate, images, consignment.getSellerMember().getNickname());
  }

  @Transactional
  public GetConsignmentDetailResponse modifyConsignment(
      Long consignmentId, Long sellerMemberId, ModifyConsignmentRequest request) {
    // TODO: consignmentService.getConsignmentById로 바꾸기
    Consignment consignment =
        consignmentRepository
            .findConsignmentById(consignmentId)
            .orElseThrow(() -> new PickUpException(CONSIGNMENT_NOT_FOUND));

    if (!consignment.getSellerMember().getMemberId().equals(sellerMemberId)) {
      throw new PickUpException(CONSIGNMENT_ACCESS_DENIED);
    }

    if (!consignment.isModifiable()) {
      throw new PickUpException(CONSIGNMENT_NOT_MODIFIABLE);
    }

    // Consignment를 수정하기 전에 인증서 값부터 검증해 불필요한 갱신을 막는다.
    Grade grade = Grade.from(request.certificate().grade());
    CertificationBody certificationBody =
        CertificationBody.from(request.certificate().certificationBody());

    Certificate certificate = getCertificate(consignment);

    consignment.updateMajorDefect(request.majorDefect());
    certificate.update(
        request.certificate().serialNumber(),
        certificationBody,
        grade,
        request.certificate().inspectedAt());
    try {
      certificateRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw new PickUpException(CERTIFICATE_SERIAL_NUMBER_ALREADY_EXISTS);
    }

    consignmentImageRepository.deleteAllByConsignment(consignment);
    List<ConsignmentImage> images =
        consignmentImageRepository.saveAll(request.toConsignmentImages(consignment));

    return GetConsignmentDetailResponse.of(
        consignment, certificate, images, consignment.getSellerMember().getNickname());
  }

  private Certificate getCertificate(Consignment consignment) {
    return certificateRepository
        .findCertificateByConsignment(consignment)
        .orElseThrow(() -> new PickUpException(CERTIFICATE_NOT_FOUND));
  }
}
