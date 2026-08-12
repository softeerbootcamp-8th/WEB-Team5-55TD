package com.ootd.pickup.bid.repository;

import com.ootd.pickup.bid.domain.BidRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BidRequestDataJpaRepository implements BidRequestRepository {

  private final BidRequestJpaRepository bidRequestJpaRepository;

  @Override
  public BidRequest save(BidRequest bidRequest) {
    return bidRequestJpaRepository.save(bidRequest);
  }

  @Override
  public Optional<BidRequest> findById(Long bidRequestId) {
    return bidRequestJpaRepository.findById(bidRequestId);
  }
}
