package com.ootd.pickup.consignments.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.repository.auction.AuctionSummary;
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
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.service.ImageService.FinalizedImage;
import com.ootd.pickup.images.service.ImageUrlResolver;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.service.MemberManageService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsignmentService {

  private final CardManageService cardManageService;
  private final ConsignmentRepository consignmentRepository;
  private final CertificateRepository certificateRepository;
  private final ConsignmentImageRepository consignmentImageRepository;
  private final MemberManageService memberManageService;
  private final ImageUrlResolver imageUrlResolver;
  private final AuctionManageService auctionManageService;

  @Transactional
  public RegisterConsignmentResponse registerConsignment(
      Long sellerMemberId,
      RegisterConsignmentRequest request,
      List<FinalizedImage> finalizedImages) {
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
                .cardState(request.cardState())
                .majorDefect(request.majorDefect())
                .status(ConsignmentStatus.REGISTERABLE)
                .build());

    Certificate certificate;
    try {
      certificate = certificateRepository.save(request.certificate().toEntity(consignment));
    } catch (DataIntegrityViolationException exception) {
      throw new PickUpException(CERTIFICATE_SERIAL_NUMBER_ALREADY_EXISTS);
    }

    List<ConsignmentImage> images = new ArrayList<>();
    for (int index = 0; index < finalizedImages.size(); index++) {
      images.add(
          ConsignmentImage.builder()
              .consignment(consignment)
              .imageOrder(index + 1)
              .objectKey(finalizedImages.get(index).objectKey())
              .build());
    }
    consignmentImageRepository.saveAll(images);

    log.info(
        "위탁 상품을 등록했습니다 - consignmentId={}, sellerMemberId={}, cardId={}",
        consignment.getConsignmentId(),
        sellerMemberId,
        card.getCardId());
    return RegisterConsignmentResponse.of(consignment, certificate);
  }

  public GetConsignmentDetailResponse getConsignment(Long consignmentId, Long sellerMemberId) {
    Consignment consignment =
        consignmentRepository
            .findConsignmentById(consignmentId)
            .orElseThrow(() -> new PickUpException(CONSIGNMENT_NOT_FOUND));

    if (!consignment.getSellerMember().getMemberId().equals(sellerMemberId)) {
      throw new PickUpException(CONSIGNMENT_READ_OWNER_MISMATCH);
    }

    Certificate certificate = getCertificate(consignment);

    List<ConsignmentImage> images =
        consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment);

    AuctionSummary auctionSummary =
        auctionManageService
            .findAuctionSummariesByConsignments(List.of(consignment))
            .get(consignment.getConsignmentId());

    return GetConsignmentDetailResponse.of(
        consignment,
        certificate,
        images,
        consignment.getSellerMember().getNickname(),
        imageUrlResolver,
        auctionSummary);
  }

  @Transactional
  public ConsignmentModificationResult modifyConsignment(
      Long consignmentId,
      Long sellerMemberId,
      ModifyConsignmentRequest request,
      List<FinalizedImage> finalizedImages) {
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
    consignment.updateCardState(request.cardState());
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

    List<ConsignmentImage> existingImages =
        consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment);
    Map<Long, ConsignmentImage> existingImageById = new HashMap<>();
    for (ConsignmentImage image : existingImages) {
      existingImageById.put(image.getConsignmentImageId(), image);
    }

    Set<Long> retainedImageIds = new HashSet<>();
    for (var imageRequest : request.images()) {
      if (!imageRequest.isValidReference()) {
        throw new PickUpException(ILLEGAL_ARGUMENT);
      }
      if (imageRequest.consignmentImageId() != null) {
        if (!existingImageById.containsKey(imageRequest.consignmentImageId())) {
          throw new PickUpException(ILLEGAL_ARGUMENT);
        }
        if (!retainedImageIds.add(imageRequest.consignmentImageId())) {
          throw new PickUpException(DUPLICATE_IMAGE_UPLOAD);
        }
      }
    }

    List<ConsignmentImage> images = new ArrayList<>();
    int finalizedIndex = 0;
    for (int index = 0; index < request.images().size(); index++) {
      var imageRequest = request.images().get(index);
      ConsignmentImage image;
      if (imageRequest.consignmentImageId() != null) {
        image = existingImageById.get(imageRequest.consignmentImageId());
        image.updateImageOrder(index + 1);
      } else {
        image =
            ConsignmentImage.builder()
                .consignment(consignment)
                .imageOrder(index + 1)
                .objectKey(finalizedImages.get(finalizedIndex++).objectKey())
                .build();
      }
      images.add(image);
    }

    List<ConsignmentImage> removedImages =
        existingImages.stream()
            .filter(image -> !retainedImageIds.contains(image.getConsignmentImageId()))
            .toList();
    if (!removedImages.isEmpty()) {
      consignmentImageRepository.deleteAll(removedImages);
    }
    images = consignmentImageRepository.saveAll(images);
    AuctionSummary auctionSummary =
        auctionManageService
            .findAuctionSummariesByConsignments(List.of(consignment))
            .get(consignment.getConsignmentId());
    GetConsignmentDetailResponse response =
        GetConsignmentDetailResponse.of(
            consignment,
            certificate,
            images,
            consignment.getSellerMember().getNickname(),
            imageUrlResolver,
            auctionSummary);
    return new ConsignmentModificationResult(
        response, removedImages.stream().map(ConsignmentImage::getObjectKey).toList());
  }

  public CursorPageResponse<GetMyConsignmentsResponse, Long> getMyConsignments(
      Long sellerMemberId, GetMyConsignmentsRequest request) {
    request.validateSize();
    ConsignmentStatus status = ConsignmentStatus.from(request.status());
    AuctionStatus auctionStatus = AuctionStatus.from(request.auctionStatus());

    List<Consignment> searchedConsignments =
        consignmentRepository.findAllBySellerMemberIdAndStatusAndLatestAuctionStatusAndCursor(
            sellerMemberId, status, auctionStatus, request.cursor(), request.size() + 1);

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

    Map<Long, AuctionSummary> auctionSummariesByConsignmentId =
        auctionManageService.findAuctionSummariesByConsignments(consignments);

    Map<Long, String> thumbnailsByConsignmentId = resolveThumbnails(consignments);

    List<GetMyConsignmentsResponse> items =
        consignments.stream()
            .map(
                consignment ->
                    GetMyConsignmentsResponse.fromEntity(
                        consignment,
                        sellerMemberId,
                        certificatesByConsignmentId.get(consignment.getConsignmentId()),
                        auctionSummariesByConsignmentId.get(consignment.getConsignmentId()),
                        thumbnailsByConsignmentId.get(consignment.getConsignmentId())))
            .toList();

    return CursorPageResponse.from(items, hasNext, nextCursor);
  }

  @Transactional
  public List<String> deleteConsignment(Long consignmentId, Long sellerMemberId) {
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

    List<String> imageObjectKeys =
        consignmentImageRepository.findAllByConsignmentOrderByImageOrderAsc(consignment).stream()
            .map(ConsignmentImage::getObjectKey)
            .toList();
    certificateRepository.deleteByConsignment(consignment);
    consignmentImageRepository.deleteAllByConsignment(consignment);
    consignmentRepository.deleteById(consignmentId);
    log.info("위탁 상품을 삭제했습니다 - consignmentId={}, sellerMemberId={}", consignmentId, sellerMemberId);
    return imageObjectKeys;
  }

  /** 경매가 예정/진행 중인 상품을 셀러로 등록해 두면, 탈퇴 후 그 경매를 아무도 관리할 수 없게 되므로 탈퇴를 막는다. */
  public boolean hasActiveConsignment(Long sellerMemberId) {
    return consignmentRepository.existsBySellerMemberIdAndStatus(
        sellerMemberId, ConsignmentStatus.IN_AUCTION);
  }

  private Certificate getCertificate(Consignment consignment) {
    return certificateRepository
        .findCertificateByConsignment(consignment)
        .orElseThrow(() -> new PickUpException(CERTIFICATE_NOT_FOUND));
  }

  private Map<Long, String> resolveThumbnails(List<Consignment> consignments) {
    if (consignments.isEmpty()) {
      return Map.of();
    }

    List<Long> consignmentIds = consignments.stream().map(Consignment::getConsignmentId).toList();
    List<ConsignmentImage> images =
        consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
            consignmentIds);

    return images.stream()
        .collect(
            Collectors.toMap(
                image -> image.getConsignment().getConsignmentId(),
                image -> imageUrlResolver.resolve(image.getObjectKey()),
                (first, second) -> first));
  }

  public record ConsignmentModificationResult(
      GetConsignmentDetailResponse response, List<String> removedObjectKeys) {}
}
