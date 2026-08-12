package com.ootd.pickup.bid.repository;

import com.ootd.pickup.bid.domain.BidRequest;
import java.util.Optional;

public interface BidRequestRepository {
  BidRequest save(BidRequest bidRequest);

  Optional<BidRequest> findById(Long bidRequestId);
}
