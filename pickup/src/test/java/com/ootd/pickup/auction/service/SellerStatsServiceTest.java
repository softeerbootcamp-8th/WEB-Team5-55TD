package com.ootd.pickup.auction.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.dto.response.SellerStatsResponse;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.consignments.service.ConsignmentManageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerStatsServiceTest {

  @Mock private AuctionRepository auctionRepository;

  @Mock private ConsignmentManageService consignmentManageService;

  private SellerStatsService sellerStatsService;

  @BeforeEach
  void setUp() {
    sellerStatsService = new SellerStatsService(auctionRepository, consignmentManageService);
  }

  @Test
  void 셀러의_통계를_각_저장소에서_집계하여_반환한다() {
    // given
    Long sellerMemberId = 1L;
    given(consignmentManageService.countRegisteredConsignments(sellerMemberId)).willReturn(12L);
    given(auctionRepository.countBySellerMemberIdAndStatus(sellerMemberId, AuctionStatus.SCHEDULED))
        .willReturn(5L);
    given(auctionRepository.countBySellerMemberIdAndStatus(sellerMemberId, AuctionStatus.ONGOING))
        .willReturn(2L);
    given(consignmentManageService.countWonConsignments(sellerMemberId)).willReturn(38L);

    // when
    SellerStatsResponse response = sellerStatsService.getMyStats(sellerMemberId);

    // then
    assertThat(response).isEqualTo(new SellerStatsResponse(12L, 5L, 2L, 38L));
  }
}
