package com.ootd.pickup.consignments.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.admin.dto.request.AdminBlockConsignmentRequest;
import com.ootd.pickup.admin.dto.request.AdminSearchConsignmentsRequest;
import com.ootd.pickup.admin.dto.response.AdminConsignmentDetailResponse;
import com.ootd.pickup.admin.dto.response.AdminConsignmentListItemResponse;
import com.ootd.pickup.auction.service.AuctionManageService;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.service.CardManageService;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.domain.Grade;
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
import com.ootd.pickup.global.dto.response.PageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.service.MemberManageService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsignmentService {

  private final CardManageService cardManageService;
  private final ConsignmentRepository consignmentRepository;
  private final CertificateRepository certificateRepository;
  private final ConsignmentImageRepository consignmentImageRepository;
  private final MemberManageService memberManageService;
  private final AuctionManageService auctionManageService;

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
    // 수정 가능 여부 확인과 갱신 사이에 상태가 바뀌지 않도록 같은 락 안에서 조회한다.
    Consignment consignment =
        consignmentRepository
            .findByIdForUpdate(consignmentId)
            .orElseThrow(() -> new PickUpException(CONSIGNMENT_NOT_FOUND));

    if (!consignment.getSellerMember().getMemberId().equals(sellerMemberId)) {
      throw new PickUpException(CONSIGNMENT_MODIFY_OWNER_MISMATCH);
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

  public CursorPageResponse<GetMyConsignmentsResponse, Long> getMyConsignments(
      Long sellerMemberId, GetMyConsignmentsRequest request) {
    request.validateSize();
    ConsignmentStatus status = ConsignmentStatus.from(request.status());

    List<Consignment> searchedConsignments =
        consignmentRepository.findAllBySellerMemberIdAndStatusAndCursor(
            sellerMemberId, status, request.cursor(), request.size() + 1);

    boolean hasNext = searchedConsignments.size() > request.size();
    List<Consignment> consignments =
        hasNext ? searchedConsignments.subList(0, request.size()) : searchedConsignments;
    Long nextCursor = hasNext ? consignments.getLast().getConsignmentId() : null;

    Map<Long, Certificate> certificatesByConsignmentId =
        certificateRepository.findAllByConsignmentIn(consignments).stream()
            .collect(
                Collectors.toMap(
                    certificate -> certificate.getConsignment().getConsignmentId(),
                    Function.identity()));

    Map<Long, Long> auctionIdsByConsignmentId =
        auctionManageService.findAuctionIdsByConsignments(consignments);

    List<GetMyConsignmentsResponse> items =
        consignments.stream()
            .map(
                consignment ->
                    GetMyConsignmentsResponse.fromEntity(
                        consignment,
                        sellerMemberId,
                        certificatesByConsignmentId.get(consignment.getConsignmentId()),
                        auctionIdsByConsignmentId.get(consignment.getConsignmentId())))
            .toList();

    return CursorPageResponse.from(items, hasNext, nextCursor);
  }

  @Transactional
  public void deleteConsignment(Long consignmentId, Long sellerMemberId) {
    // 삭제 가능 여부 확인과 삭제 사이에 상태가 바뀌지 않도록 같은 락 안에서 조회한다.
    Consignment consignment =
        consignmentRepository
            .findByIdForUpdate(consignmentId)
            .orElseThrow(() -> new PickUpException(CONSIGNMENT_NOT_FOUND));

    if (!consignment.getSellerMember().getMemberId().equals(sellerMemberId)) {
      throw new PickUpException(CONSIGNMENT_DELETE_OWNER_MISMATCH);
    }

    if (!consignment.isDeletable()) {
      throw new PickUpException(CONSIGNMENT_NOT_DELETABLE);
    }

    certificateRepository.deleteByConsignment(consignment);
    consignmentImageRepository.deleteAllByConsignment(consignment);
    consignmentRepository.deleteById(consignmentId);
  }

  private Certificate getCertificate(Consignment consignment) {
    return certificateRepository
        .findCertificateByConsignment(consignment)
        .orElseThrow(() -> new PickUpException(CERTIFICATE_NOT_FOUND));
  }

  public PageResponse<AdminConsignmentListItemResponse> searchConsignmentsForAdmin(
      AdminSearchConsignmentsRequest request, Pageable pageable) {
    List<ConsignmentStatus> statuses =
        request.status() == null
            ? List.of()
            : request.status().stream().map(ConsignmentStatus::from).toList();

    Page<Consignment> consignments =
        consignmentRepository.searchConsignmentsForAdmin(
            request.q(), statuses, request.sellerMemberId(), pageable);

    return PageResponse.from(consignments, AdminConsignmentListItemResponse::fromEntity);
  }

  public AdminConsignmentDetailResponse getConsignmentDetailForAdmin(Long consignmentId) {
    Consignment consignment = getConsignmentEntity(consignmentId);
    Certificate certificate = getCertificate(consignment);
    return AdminConsignmentDetailResponse.of(consignment, certificate);
  }

  @Transactional
  public AdminConsignmentDetailResponse blockConsignment(
      Long adminId, Long consignmentId, AdminBlockConsignmentRequest request) {
    Consignment consignment =
        consignmentRepository
            .findByIdForUpdate(consignmentId)
            .orElseThrow(() -> new PickUpException(CONSIGNMENT_NOT_FOUND));

    consignment.block();

    log.info(
        "상품 강제 차단 - adminId={}, consignmentId={}, reason={}",
        adminId,
        consignmentId,
        request.reason());

    Certificate certificate = getCertificate(consignment);
    return AdminConsignmentDetailResponse.of(consignment, certificate);
  }

  @Transactional
  public AdminConsignmentDetailResponse unblockConsignment(Long adminId, Long consignmentId) {
    Consignment consignment =
        consignmentRepository
            .findByIdForUpdate(consignmentId)
            .orElseThrow(() -> new PickUpException(CONSIGNMENT_NOT_FOUND));

    consignment.unblock();

    log.info("상품 차단 해제 - adminId={}, consignmentId={}", adminId, consignmentId);

    Certificate certificate = getCertificate(consignment);
    return AdminConsignmentDetailResponse.of(consignment, certificate);
  }

  private Consignment getConsignmentEntity(Long consignmentId) {
    return consignmentRepository
        .findConsignmentById(consignmentId)
        .orElseThrow(() -> new PickUpException(CONSIGNMENT_NOT_FOUND));
  }
}
