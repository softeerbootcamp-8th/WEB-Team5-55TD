package com.ootd.pickup.settlement.handler;

import static com.ootd.pickup.auction.domain.AuctionStatus.PASSED;
import static com.ootd.pickup.auction.domain.AuctionStatus.WON;

import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.global.observability.SettlementHandlerMetrics;
import com.ootd.pickup.point.service.PointReservationService;
import com.ootd.pickup.settlement.service.SettlementService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * {@link AuctionEndedMessageQueueEvent} 수신 어댑터.
 *
 * <p>유찰 이벤트는 활성 포인트 예약을 해제하고, 낙찰 이벤트는 구매자 결제와 판매자 지급 정산을 수행한다. 두 처리 모두 재전달에 안전한 멱등 서비스에 위임한다.
 *
 * <p>이벤트를 어떻게 받는지(SQS 컨슈머가 역직렬화해 이 {@link #handle}을 호출)는 여기서만 알고, {@link SettlementService}는 {@code
 * DomainEvent}/{@code EventHandler}를 몰라도 되게 한다. {@code RestController}가 HTTP 요청/응답을 다루고 {@code
 * Service}는 순수 비즈니스 로직만 다루는 것과 같은 이유다.
 *
 * <p>인스턴스가 여러 대면 같은 경매의 정산 이벤트가 서로 다른 인스턴스에서 동시에 처리될 수 있다. 이때 뒤늦은 쪽은 {@code settlement} 테이블의 유니크
 * 제약에 걸려 {@link DataIntegrityViolationException}과 함께 트랜잭션 전체가 롤백된다({@link SettlementService} 참고). 이
 * 중 정산 멱등성 제약({@code uk_settlement_auction_member_type}) 위반만 다른 인스턴스가 이미 같은 정산을 끝냈다는 신호로 해석한다.
 * 트랜잭션이 이미 안전하게 롤백된 이 시점(트랜잭션 경계 밖)에서 잡아 정상 소비로 처리한다. 다른 무결성 제약 위반까지 중복 정산으로 간주하면 데이터 오류가 성공으로 오인되어
 * 메시지가 삭제되므로 그대로 다시 던진다. 여기서 잡지 않으면 {@code SQSEventConsumer}가 이를 알 수 없는 실패로 보고 error 로그(Slack 알림)를
 * 남기고 메시지 그룹을 막아, 자기 치유되는 경합인데도 소음과 지연을 만든다.
 *
 * <p>처리 소요 시간을 {@link SettlementHandlerMetrics}로 기록한다. SQS {@code visibility-timeout}(현재 30초)이 이
 * 시간보다 길어야 하는데, 그 값이 이 핸들러가 없던 시절 실측 없이 임의로 정해진 값이라({@code docs/SQS_가시성_타임아웃_실측_실행_계획.md}), 재산정에 쓸
 * 실제 처리 시간 분포가 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEventHandler implements EventHandler<AuctionEndedMessageQueueEvent> {

  private static final String SETTLEMENT_IDEMPOTENCY_CONSTRAINT =
      "uk_settlement_auction_member_type";

  /** 이 시간을 넘긴 개별 이벤트는 원인 분석을 위해 로그에 남긴다. 잠정값 — 실측 후 조정한다. */
  private static final long SLOW_THRESHOLD_MILLIS = 5_000L;

  private final SettlementService settlementService;
  private final PointReservationService pointReservationService;
  private final SettlementHandlerMetrics settlementHandlerMetrics;

  @Override
  public Class<AuctionEndedMessageQueueEvent> eventClass() {
    return AuctionEndedMessageQueueEvent.class;
  }

  @Override
  public void handle(AuctionEndedMessageQueueEvent event) {
    log.debug(
        "경매 종료 이벤트를 수신했습니다 - eventId={}, auctionId={}, auctionStatus={}",
        event.eventId(),
        event.auctionId(),
        event.auctionStatus());

    long startNanos = System.nanoTime();
    boolean success = true;
    try {
      dispatch(event);
    } catch (RuntimeException exception) {
      success = false;
      throw exception;
    } finally {
      recordDuration(event, success, startNanos);
    }
  }

  private void dispatch(AuctionEndedMessageQueueEvent event) {
    if (event.auctionStatus() == PASSED) {
      pointReservationService.releaseForPassedAuction(event.auctionId());
      return;
    }
    if (event.auctionStatus() != WON) {
      throw new IllegalArgumentException("종료 상태가 아닌 경매 이벤트입니다.");
    }

    try {
      settlementService.settleAuction(
          event.auctionId(), event.winnerMemberId(), event.sellerMemberId(), event.winningPrice());
    } catch (DataIntegrityViolationException exception) {
      if (!isSettlementIdempotencyViolation(exception)) {
        throw exception;
      }
      log.info(
          "다른 인스턴스가 동시에 처리한 정산이라 건너뜀 - auctionId={}, eventId={}",
          event.auctionId(),
          event.eventId());
    }
  }

  private boolean isSettlementIdempotencyViolation(DataIntegrityViolationException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof ConstraintViolationException constraintViolationException) {
        return SETTLEMENT_IDEMPOTENCY_CONSTRAINT.equals(
            constraintViolationException.getConstraintName());
      }
      cause = cause.getCause();
    }
    return false;
  }

  private void recordDuration(
      AuctionEndedMessageQueueEvent event, boolean success, long startNanos) {
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
    settlementHandlerMetrics.recordDuration(event.auctionStatus(), success, elapsed);

    if (elapsed.toMillis() > SLOW_THRESHOLD_MILLIS) {
      log.warn(
          "정산 이벤트 처리가 느립니다 - auctionId={}, auctionStatus={}, elapsedMillis={}",
          event.auctionId(),
          event.auctionStatus(),
          elapsed.toMillis());
    }
  }
}
