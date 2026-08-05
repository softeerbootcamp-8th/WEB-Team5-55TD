package com.ootd.pickup.settlement.handler;

import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link AuctionEndedMessageQueueEvent} 수신 어댑터.
 *
 * <p>이벤트를 어떻게 받는지(SQS 컨슈머가 역직렬화해 이 {@link #handle}을 호출)는 여기서만 알고, {@link SettlementService}는 {@code
 * DomainEvent}/{@code EventHandler}를 몰라도 되게 한다. {@code RestController}가 HTTP 요청/응답을 다루고 {@code
 * Service}는 순수 비즈니스 로직만 다루는 것과 같은 이유다.
 */
@Component
@RequiredArgsConstructor
public class SettlementEventHandler implements EventHandler<AuctionEndedMessageQueueEvent> {

  private final SettlementService settlementService;

  @Override
  public Class<AuctionEndedMessageQueueEvent> eventClass() {
    return AuctionEndedMessageQueueEvent.class;
  }

  @Override
  public void handle(AuctionEndedMessageQueueEvent event) {
    settlementService.settleAuction(
        event.auctionId(), event.winnerMemberId(), event.sellerMemberId(), event.winningPrice());
  }
}
