package com.ootd.pickup.consignments.service;

import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsignmentManageService {

  private final ConsignmentRepository consignmentRepository;

  public long countRegisteredConsignments(Long sellerMemberId) {
    return consignmentRepository.countBySellerMemberIdAndStatus(
        sellerMemberId, ConsignmentStatus.REGISTERABLE);
  }

  public long countWonConsignments(Long sellerMemberId) {
    return consignmentRepository.countBySellerMemberIdAndStatus(
        sellerMemberId, ConsignmentStatus.WON);
  }
}
