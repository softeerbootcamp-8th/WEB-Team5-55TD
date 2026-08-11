package com.ootd.pickup.bid.service;

import static com.ootd.pickup.global.exception.ExceptionCode.OUTBID_EXISTS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.domain.BidRequestStatus;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.repository.BidRequestRepository;
import com.ootd.pickup.global.exception.PickUpException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BidRequestProcessingServiceTest {

  @Mock private BidRequestRepository bidRequestRepository;
  @Mock private BidService bidService;
  @Mock private BidRequestStatusService statusService;

  private BidRequestProcessingService processingService;

  @BeforeEach
  void setUp() {
    processingService =
        new BidRequestProcessingService(bidRequestRepository, bidService, statusService);
  }

  @Test
  void 대기중인_요청은_실제_입찰을_수행하고_성공으로_기록한다() {
    // given
    BidRequestCreatedMessageQueueEvent event = createEvent(10L, 1L, 2L, 10_500L);
    given(bidRequestRepository.findById(10L)).willReturn(Optional.of(pendingBidRequest(10L)));

    // when
    processingService.process(event);

    // then
    then(bidService).should().placeBid(eq(1L), eq(2L), eq(new PlaceBidRequest(10_500L)), eq(10L));
    then(statusService).should().markSucceeded(10L);
    then(statusService).should(never()).markFailed(any(), any());
  }

  @Test
  void 이미_처리된_요청은_다시_처리하지_않는다() {
    // given
    BidRequestCreatedMessageQueueEvent event = createEvent(10L, 1L, 2L, 10_500L);
    BidRequest succeeded = pendingBidRequest(10L);
    succeeded.succeed();
    given(bidRequestRepository.findById(10L)).willReturn(Optional.of(succeeded));

    // when
    processingService.process(event);

    // then
    then(bidService).should(never()).placeBid(any(), any(), any(), any());
    then(statusService).should(never()).markSucceeded(any());
    then(statusService).should(never()).markFailed(any(), any());
  }

  @Test
  void 입찰_처리가_실패하면_실패로_기록한다() {
    // given
    BidRequestCreatedMessageQueueEvent event = createEvent(10L, 1L, 2L, 10_500L);
    given(bidRequestRepository.findById(10L)).willReturn(Optional.of(pendingBidRequest(10L)));
    PickUpException exception = new PickUpException(OUTBID_EXISTS);
    willThrow(exception)
        .given(bidService)
        .placeBid(eq(1L), eq(2L), eq(new PlaceBidRequest(10_500L)), eq(10L));

    // when
    processingService.process(event);

    // then
    then(statusService).should().markFailed(event, exception);
    then(statusService).should(never()).markSucceeded(any());
  }

  @Test
  void 존재하지_않는_BidRequest면_예외가_발생한다() {
    // given
    BidRequestCreatedMessageQueueEvent event = createEvent(999L, 1L, 2L, 10_500L);
    given(bidRequestRepository.findById(999L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> processingService.process(event))
        .isInstanceOf(IllegalStateException.class);
    then(bidService).should(never()).placeBid(any(), any(), any(), any());
  }

  private BidRequest pendingBidRequest(Long bidRequestId) {
    BidRequest bidRequest = BidRequest.create(1L, 2L, 10_500L);
    ReflectionTestUtils.setField(bidRequest, "bidRequestId", bidRequestId);
    return bidRequest;
  }

  private BidRequestCreatedMessageQueueEvent createEvent(
      Long bidRequestId, Long auctionId, Long memberId, Long bidPrice) {
    return new BidRequestCreatedMessageQueueEvent(
        "event-id", bidRequestId, auctionId, memberId, bidPrice, LocalDateTime.now());
  }
}
