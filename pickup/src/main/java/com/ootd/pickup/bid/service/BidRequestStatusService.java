package com.ootd.pickup.bid.service;

import com.ootd.pickup.bid.domain.BidRequest;
import com.ootd.pickup.bid.event.BidRequestCreatedMessageQueueEvent;
import com.ootd.pickup.bid.event.BidRequestFailedNotificationEvent;
import com.ootd.pickup.bid.repository.BidRequestRepository;
import com.ootd.pickup.global.exception.PickUpException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code BidRequest} 상태 갱신을 별도 빈으로 분리했다.
 *
 * <p>{@link BidRequestProcessingService#process}가 자기 자신의 메서드를 호출하는 형태로 이 메서드들을 두면 Spring AOP 프록시를
 * 거치지 않아 {@code @Transactional}이 적용되지 않는다(self-invocation). 그래서 별도 빈으로 분리해 항상 프록시를 통해 호출되게 한다.
 */
@Service
@RequiredArgsConstructor
public class BidRequestStatusService {

  private final BidRequestRepository bidRequestRepository;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Transactional
  public void markSucceeded(Long bidRequestId) {
    bidRequestRepository.findById(bidRequestId).ifPresent(BidRequest::succeed);
  }

  @Transactional
  public void markFailed(BidRequestCreatedMessageQueueEvent event, PickUpException exception) {
    bidRequestRepository
        .findById(event.bidRequestId())
        .ifPresent(
            bidRequest ->
                bidRequest.fail(exception.getExceptionCodeName(), exception.getMessage()));
    applicationEventPublisher.publishEvent(
        BidRequestFailedNotificationEvent.from(event, exception));
  }
}
