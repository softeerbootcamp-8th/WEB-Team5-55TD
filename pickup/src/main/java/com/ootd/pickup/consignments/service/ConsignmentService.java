package com.ootd.pickup.consignments.service;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.service.CardManageService;
import com.ootd.pickup.consignments.domain.*;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.repository.certificate.CertificateRepository;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import com.ootd.pickup.consignments.repository.consignmentImage.ConsignmentImageRepository;
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

    // TODO: 인증 구현 이후 memberId 제외시키기
    @Transactional
    public RegisterConsignmentResponse registerConsignment(Long sellerMemberId, RegisterConsignmentRequest request) {
        Card card = cardManageService.getCardByCardId(request.cardId());
        // Consignment를 저장하기 전에 인증서 값부터 검증해 불필요한 INSERT를 막는다.
        Grade.from(request.certificate().grade());
        CertificationBody.from(request.certificate().certificationBody());

        Consignment consignment = consignmentRepository.save(
                Consignment.builder()
                        .card(card)
                        .sellerMemberId(sellerMemberId)
                        .majorDefect(request.majorDefect())
                        .status(ConsignmentStatus.REGISTERABLE)
                        .build()
        );

        Certificate certificate = certificateRepository.save(request.certificate().toEntity(consignment));

        consignmentImageRepository.saveAll(request.toConsignmentImages(consignment));

        return RegisterConsignmentResponse.of(consignment, certificate);
    }
}
