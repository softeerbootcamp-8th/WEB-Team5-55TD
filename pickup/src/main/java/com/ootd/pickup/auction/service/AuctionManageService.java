package com.ootd.pickup.auction.service;

import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionManageService {

  private final AuctionRepository auctionRepository;

  public Map<Long, Long> findAuctionIdsByConsignments(List<Consignment> consignments) {
    return auctionRepository.findAuctionIdsByConsignmentIn(consignments);
  }
}
