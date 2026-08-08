package com.ootd.pickup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.repository.featured.FeaturedAuctionCacheRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeaturedAuctionFacadeTest {

  @Mock private FeaturedAuctionCacheRepository cacheRepository;

  @Mock private AuctionService auctionService;

  @InjectMocks private FeaturedAuctionFacade featuredAuctionFacade;

  @Test
  @DisplayName("캐시 히트 시 DB 서치 쿼리 없이 캐시된 경매 ID로 단건 조회한다")
  void getFeaturedAuction_cacheHit() {
    // given
    Long cachedAuctionId = 100L;
    AuctionListItemResponse expectedResponse = mock(AuctionListItemResponse.class);
    given(expectedResponse.auctionId()).willReturn(cachedAuctionId);

    given(cacheRepository.getFeaturedAuctionId()).willReturn(Optional.of(cachedAuctionId));
    given(auctionService.getFeaturedAuctionById(cachedAuctionId, null))
        .willReturn(expectedResponse);

    // when
    AuctionListItemResponse response = featuredAuctionFacade.getFeaturedAuction(null);

    // then
    assertThat(response.auctionId()).isEqualTo(cachedAuctionId);
    verify(auctionService, never()).getFeaturedAuctionFromDb(any());
  }

  @Test
  @DisplayName("캐시 미스 시 DB 서치 쿼리를 수행하고 결과를 Redis에 저장한다")
  void getFeaturedAuction_cacheMiss() {
    // given
    Long dbAuctionId = 200L;
    AuctionListItemResponse expectedResponse = mock(AuctionListItemResponse.class);
    given(expectedResponse.auctionId()).willReturn(dbAuctionId);

    given(cacheRepository.getFeaturedAuctionId()).willReturn(Optional.empty());
    given(auctionService.getFeaturedAuctionFromDb(null)).willReturn(expectedResponse);

    // when
    AuctionListItemResponse response = featuredAuctionFacade.getFeaturedAuction(null);

    // then
    assertThat(response.auctionId()).isEqualTo(dbAuctionId);
    verify(cacheRepository).setFeaturedAuctionId(eq(dbAuctionId), any());
  }
}
