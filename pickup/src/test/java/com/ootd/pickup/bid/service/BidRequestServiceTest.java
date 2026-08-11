package com.ootd.pickup.bid.service;

import static com.ootd.pickup.global.exception.ExceptionCode.AUCTION_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.domain.BidRequestStatus;
import com.ootd.pickup.bid.dto.response.CreateBidRequestResponse;
import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.repository.BidRequestRepository;
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.exception.PickUpException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BidRequestServiceTest {

  @Mock private AuctionRepository auctionRepository;
  @Mock private BidRequestRepository bidRequestRepository;
  @Mock private EventProducer eventProducer;

  private BidRequestService bidRequestService;

  @BeforeEach
  void setUp() {
    bidRequestService = new BidRequestService(auctionRepository, bidRequestRepository, eventProducer);
  }

  @Test
  void 존재하는_경매에_입찰_요청을_만들면_저장하고_Outbox에_적재한다() {
    // given
    given(auctionRepository.findById(1L)).willReturn(Optional.of(mock(Auction.class)));
    given(bidRequestRepository.save(any(BidRequest.class)))
        .willAnswer(
            invocation -> {
              BidRequest bidRequest = invocation.getArgument(0);
              ReflectionTestUtils.setField(bidRequest, "bidRequestId", 10L);
              return bidRequest;
            });

    // when
    CreateBidRequestResponse response = bidRequestService.createBidRequest(1L, 2L, 10_500L);

    // then
    assertThat(response.bidRequestId()).isEqualTo(10L);
    assertThat(response.auctionId()).isEqualTo(1L);
    assertThat(response.memberId()).isEqualTo(2L);
    assertThat(response.bidPrice()).isEqualTo(10_500L);
    assertThat(response.status()).isEqualTo(BidRequestStatus.PENDING);

    ArgumentCaptor<BidRequestCreatedMessageQueueEvent> eventCaptor =
        ArgumentCaptor.forClass(BidRequestCreatedMessageQueueEvent.class);
    then(eventProducer).should().produce(eventCaptor.capture());
    BidRequestCreatedMessageQueueEvent event = eventCaptor.getValue();
    assertThat(event.bidRequestId()).isEqualTo(10L);
    assertThat(event.auctionId()).isEqualTo(1L);
    assertThat(event.memberId()).isEqualTo(2L);
    assertThat(event.bidPrice()).isEqualTo(10_500L);
  }

  @Test
  void 존재하지_않는_경매에_입찰_요청을_만들면_예외가_발생한다() {
    // given
    given(auctionRepository.findById(999L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> bidRequestService.createBidRequest(999L, 2L, 10_500L))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(AUCTION_NOT_FOUND.getClientExceptionCode().name()));
    then(bidRequestRepository).should(never()).save(any(BidRequest.class));
    then(eventProducer).should(never()).produce(any());
  }
}
