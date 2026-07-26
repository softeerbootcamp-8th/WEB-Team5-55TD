package com.ootd.pickup.consignments.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.service.CardManageService;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.repository.CertificateRepository;
import com.ootd.pickup.consignments.repository.ConsignmentImageRepository;
import com.ootd.pickup.consignments.repository.ConsignmentRepository;

import lombok.RequiredArgsConstructor;

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

        Consignment consignment = consignmentRepository.save(
                Consignment.builder()
                        .card(card)
                        .sellerMemberId(sellerMemberId)
                        .majorDefect(request.majorDefect())
                        .status(ConsignmentStatus.REGISTERABLE)
                        .build()
        );

        Certificate certificate = certificateRepository.save(request.certificate().toEntity(consignment));

        consignmentImageRepository.saveAll(
                request.images().stream()
                        .map(imageRequest -> imageRequest.toEntity(consignment))
                        .toList()
        );

        return RegisterConsignmentResponse.of(consignment, certificate);
    }
}
