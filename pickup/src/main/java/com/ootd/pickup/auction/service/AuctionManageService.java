package com.ootd.pickup.auction.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.auction.repository.auction.AuctionSummary;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.global.exception.PickUpException;
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

  public Auction getAuctionById(Long auctionId) {
    return auctionRepository
        .findById(auctionId)
        .orElseThrow(() -> new PickUpException(AUCTION_NOT_FOUND));
  }

  public Map<Long, AuctionSummary> findAuctionSummariesByConsignments(
      List<Consignment> consignments) {
    return auctionRepository.findAuctionSummariesByConsignmentIn(consignments);
  }

  /**
   * 위탁 상품에 경매 이력(과거 유찰 포함)이 한 번이라도 있었는지 확인한다. 유찰된 상품은 다시 REGISTERABLE 상태로 돌아가지만, 그 상품을 참조하는 과거 경매
   * 행은 FK로 계속 남아 있어 그대로 삭제하면 무결성 제약 위반이 발생한다. 삭제 가능 여부를 판단하기 전에 반드시 호출해야 한다.
   */
  public boolean hasAuctionHistory(Consignment consignment) {
    return auctionRepository.existsByConsignment(consignment);
  }
}
