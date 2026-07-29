package com.ootd.pickup.auction.repository.auction;

import com.ootd.pickup.auction.domain.Auction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuctionDataJpaRepository implements AuctionRepository {

  private final AuctionJpaRepository auctionJpaRepository;

  @Override
  public Auction save(Auction auction) {
    return auctionJpaRepository.save(auction);
  }
}
