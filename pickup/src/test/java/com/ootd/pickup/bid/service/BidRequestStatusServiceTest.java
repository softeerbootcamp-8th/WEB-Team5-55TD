package com.ootd.pickup.bid.service;

import static com.ootd.pickup.global.exception.ExceptionCode.OUTBID_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.domain.BidRequestStatus;
import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.event.BidRequestFailedNotificationEvent;
import com.ootd.pickup.bid.repository.BidRequestRepository;
import com.ootd.pickup.global.exception.PickUpException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BidRequestStatusServiceTest {

  @Mock private BidRequestRepository bidRequestRepository;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private BidRequestStatusService statusService;

  @BeforeEach
  void setUp() {
    statusService = new BidRequestStatusService(bidRequestRepository, applicationEventPublisher);
  }

  @Test
  void 대기중인_요청을_성공으로_기록한다() {
    // given
    BidRequest bidRequest = bidRequest(10L);
    given(bidRequestRepository.findById(10L)).willReturn(Optional.of(bidRequest));

    // when
    statusService.markSucceeded(10L);

    // then
    assertThat(bidRequest.getStatus()).isEqualTo(BidRequestStatus.SUCCEEDED);
  }

  @Test
  void 대기중인_요청을_실패로_기록하고_실패_알림을_발행한다() {
    // given
    BidRequest bidRequest = bidRequest(10L);
    given(bidRequestRepository.findById(10L)).willReturn(Optional.of(bidRequest));
    BidRequestCreatedMessageQueueEvent event =
        new BidRequestCreatedMessageQueueEvent(
            "event-id", 10L, 1L, 2L, 10_500L, LocalDateTime.now());
    PickUpException exception = new PickUpException(OUTBID_EXISTS);

    // when
    statusService.markFailed(event, exception);

    // then
    assertThat(bidRequest.getStatus()).isEqualTo(BidRequestStatus.FAILED);
    assertThat(bidRequest.getFailureCode()).isEqualTo(OUTBID_EXISTS.getClientExceptionCode().name());

    ArgumentCaptor<BidRequestFailedNotificationEvent> eventCaptor =
        ArgumentCaptor.forClass(BidRequestFailedNotificationEvent.class);
    then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
    BidRequestFailedNotificationEvent published = eventCaptor.getValue();
    assertThat(published.bidRequestId()).isEqualTo(10L);
    assertThat(published.memberId()).isEqualTo(2L);
    assertThat(published.failureCode()).isEqualTo(OUTBID_EXISTS.getClientExceptionCode().name());
  }

  private BidRequest bidRequest(Long bidRequestId) {
    BidRequest bidRequest = BidRequest.create(1L, 2L, 10_500L);
    ReflectionTestUtils.setField(bidRequest, "bidRequestId", bidRequestId);
    return bidRequest;
  }
}
