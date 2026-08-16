package com.ootd.pickup.bid.service;

import static com.ootd.pickup.global.exception.ExceptionCode.OUTBID_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.repository.BidRequestRepository;
import com.ootd.pickup.global.exception.PickUpException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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

  @Test
  void 같은_그룹_배치가_전부_성공하면_한_번의_시도로_끝난다() {
    // given
    BidRequestCreatedMessageQueueEvent event1 = createEvent(10L, 1L, 2L, 10_500L);
    BidRequestCreatedMessageQueueEvent event2 = createEvent(11L, 1L, 3L, 11_000L);
    given(bidRequestRepository.findById(10L)).willReturn(Optional.of(pendingBidRequest(10L)));
    given(bidRequestRepository.findById(11L)).willReturn(Optional.of(pendingBidRequest(11L)));

    // when
    List<BidRequestCreatedMessageQueueEvent> done =
        processingService.placeBidsForGroup(List.of(event1, event2));

    // then
    assertThat(done).containsExactly(event1, event2);
    then(bidService).should(times(1)).placeBid(eq(1L), eq(2L), any(), eq(10L));
    then(bidService).should(times(1)).placeBid(eq(1L), eq(3L), any(), eq(11L));
    then(statusService).should().markSucceeded(10L);
    then(statusService).should().markSucceeded(11L);
  }

  @Test
  void 배치_중_업무상_실패는_트랜잭션을_롤백시키지_않고_나머지도_함께_처리된다() {
    // given
    BidRequestCreatedMessageQueueEvent event1 = createEvent(10L, 1L, 2L, 10_500L);
    BidRequestCreatedMessageQueueEvent event2 = createEvent(11L, 1L, 3L, 11_000L);
    BidRequestCreatedMessageQueueEvent event3 = createEvent(12L, 1L, 4L, 11_500L);
    given(bidRequestRepository.findById(10L)).willReturn(Optional.of(pendingBidRequest(10L)));
    given(bidRequestRepository.findById(11L)).willReturn(Optional.of(pendingBidRequest(11L)));
    given(bidRequestRepository.findById(12L)).willReturn(Optional.of(pendingBidRequest(12L)));
    PickUpException businessFailure = new PickUpException(OUTBID_EXISTS);
    // event1/event3용 placeBid 호출은 별도로 스터빙하지 않는다(기본 동작인 정상 반환으로 충분) - lenient()는
    // 그 호출들이 이 스터빙과 인자가 다르다고 엄격 스터빙이 오탐하지 않게 한다
    lenient().doThrow(businessFailure).when(bidService).placeBid(eq(1L), eq(3L), any(), eq(11L));

    // when
    List<BidRequestCreatedMessageQueueEvent> done =
        processingService.placeBidsForGroup(List.of(event1, event2, event3));

    // then — 배치를 한 번의 시도로 끝낸다(건별 폴백으로 넘어가지 않는다). PickUpException은 placeBidsTogether를
    // 롤백시키지 않으므로 앞뒤 요청도 같은 시도 안에서 정상 처리된다
    assertThat(done).containsExactly(event1, event2, event3);
    then(statusService).should().markSucceeded(10L);
    then(statusService).should().markFailed(event2, businessFailure);
    then(statusService).should().markSucceeded(12L);
    then(bidService).should(times(1)).placeBid(eq(1L), eq(2L), any(), eq(10L));
    then(bidService).should(times(1)).placeBid(eq(1L), eq(4L), any(), eq(12L));
  }

  @Test
  void 배치_중_예기치_못한_실패는_건별_재시도로_폴백하고_재전달_중복은_성공으로_정리한다() {
    // given — event2는 재전달로 이미 처리된 상황(DataIntegrityViolationException)을 흉내낸다
    BidRequestCreatedMessageQueueEvent event1 = createEvent(10L, 1L, 2L, 10_500L);
    BidRequestCreatedMessageQueueEvent event2 = createEvent(11L, 1L, 3L, 11_000L);
    BidRequestCreatedMessageQueueEvent event3 = createEvent(12L, 1L, 4L, 11_500L);
    given(bidRequestRepository.findById(10L)).willReturn(Optional.of(pendingBidRequest(10L)));
    given(bidRequestRepository.findById(11L)).willReturn(Optional.of(pendingBidRequest(11L)));
    given(bidRequestRepository.findById(12L)).willReturn(Optional.of(pendingBidRequest(12L)));
    lenient()
        .doThrow(new DataIntegrityViolationException("duplicate"))
        .when(bidService)
        .placeBid(eq(1L), eq(3L), any(), eq(11L));

    // when
    List<BidRequestCreatedMessageQueueEvent> done =
        processingService.placeBidsForGroup(List.of(event1, event2, event3));

    // then — event1/event2는 "함께" 시도에서 이미 한 번 처리됐다가(예외로 event3는 도달하지 못한 채) 건별 재시도에서
    // 처음부터 다시 시도된다. event3는 "함께" 시도에서 도달조차 못 했으므로 건별 재시도에서 딱 한 번만 처리된다
    assertThat(done).containsExactly(event1, event2, event3);
    then(bidService).should(times(2)).placeBid(eq(1L), eq(2L), any(), eq(10L));
    then(bidService).should(times(2)).placeBid(eq(1L), eq(3L), any(), eq(11L));
    then(bidService).should(times(1)).placeBid(eq(1L), eq(4L), any(), eq(12L));
    then(statusService).should(times(2)).markSucceeded(10L);
    then(statusService).should().markSucceeded(11L);
    then(statusService).should(times(1)).markSucceeded(12L);
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
