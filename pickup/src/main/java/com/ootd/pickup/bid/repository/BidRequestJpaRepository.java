package com.ootd.pickup.bid.repository;

import com.ootd.pickup.bid.domain.BidRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRequestJpaRepository extends JpaRepository<BidRequest, Long> {}
