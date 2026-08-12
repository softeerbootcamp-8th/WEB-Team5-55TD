package com.ootd.pickup.settlement.handler;

import static com.ootd.pickup.auction.domain.AuctionStatus.PASSED;
import static com.ootd.pickup.auction.domain.AuctionStatus.WON;

import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.point.service.PointReservationService;
import com.ootd.pickup.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 제약에 걸려 {@link DataIntegrityViolationException}과 함께 트랜잭션 전체가 롤백된다({@link SettlementService} 참고).
 * 이는 실패가 아니라 다른 인스턴스가 이미 같은 정산을 끝냈다는 신호이므로, 트랜잭션이 이미 안전하게 롤백된 이 시점(트랜잭션 경계 밖)에서 잡아 정상 소비로 처리한다. 여기서
 * 잡지 않으면 {@code SQSEventConsumer}가 이를 알 수 없는 실패로 보고 error 로그(Slack 알림)를 남기고 메시지 그룹을 막아, 자기 치유되는
 * 경합인데도 소음과 지연을 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEventHandler implements EventHandler<AuctionEndedMessageQueueEvent> {

  private final SettlementService settlementService;
  private final PointReservationService pointReservationService;

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
      log.info(
          "다른 인스턴스가 동시에 처리한 정산이라 건너뜀 - auctionId={}, eventId={}",
          event.auctionId(),
          event.eventId());
    }
  }
}
