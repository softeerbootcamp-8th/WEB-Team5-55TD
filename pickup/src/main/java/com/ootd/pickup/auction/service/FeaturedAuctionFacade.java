package com.ootd.pickup.auction.service;

import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.repository.featured.FeaturedAuctionCacheRepository;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeaturedAuctionFacade {

  private static final Duration CACHE_TTL = Duration.ofSeconds(10);

  private final FeaturedAuctionCacheRepository cacheRepository;
  private final AuctionService auctionService;

  public AuctionListItemResponse getFeaturedAuction(Long viewerMemberId) {
    // 1. Check Redis Cache OUTSIDE DB Transaction
    Optional<Long> cachedAuctionId = cacheRepository.getFeaturedAuctionId();
    if (cachedAuctionId.isPresent()) {
      try {
        return auctionService.getFeaturedAuctionById(cachedAuctionId.get(), viewerMemberId);
      } catch (Exception ignored) {
        cacheRepository.evictFeaturedAuctionId();
      }
    }

    // 2. Cache Miss: Execute DB search query inside AuctionService (@Transactional(readOnly =
    // true))
    AuctionListItemResponse response = auctionService.getFeaturedAuctionFromDb(viewerMemberId);

    // 3. Cache Result OUTSIDE DB Transaction
    cacheRepository.setFeaturedAuctionId(response.auctionId(), CACHE_TTL);

    return response;
  }
}
