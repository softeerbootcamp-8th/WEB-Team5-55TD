package com.ootd.pickup.settlement.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.settlement.service.SettlementService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementEventHandlerTest {

  @Mock private SettlementService settlementService;

  private SettlementEventHandler settlementEventHandler;

  @BeforeEach
  void setUp() {
    settlementEventHandler = new SettlementEventHandler(settlementService);
  }

  @Test
  void eventClass를_호출하면_AuctionEndedMessageQueueEvent를_반환한다() {
    // when & then
    assertThat(settlementEventHandler.eventClass()).isEqualTo(AuctionEndedMessageQueueEvent.class);
  }

  @Test
  void 이벤트를_받으면_필드를_꺼내_정산_서비스에_위임한다() {
    // given
    AuctionEndedMessageQueueEvent event = createEndedEvent(1L, 2L, 3L, 10_500L);

    // when
    settlementEventHandler.handle(event);

    // then
    then(settlementService).should().settleAuction(1L, 2L, 3L, 10_500L);
  }

  private AuctionEndedMessageQueueEvent createEndedEvent(
      Long auctionId, Long winnerMemberId, Long sellerMemberId, Long winningPrice) {
    return new AuctionEndedMessageQueueEvent(
        "event-id",
        auctionId,
        100L,
        sellerMemberId,
        10_000L,
        10_000L,
        10L,
        winnerMemberId,
        winningPrice,
        AuctionStatus.WON,
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now(),
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now());
  }
}
