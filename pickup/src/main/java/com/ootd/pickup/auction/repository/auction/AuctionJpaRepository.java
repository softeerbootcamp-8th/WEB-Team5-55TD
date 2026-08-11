package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.Auction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionJpaRepository extends JpaRepository<Auction, Long> {}
