package com.ootd.pickup.settlement.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.point.service.PointReservationService;
import com.ootd.pickup.settlement.service.SettlementService;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SettlementEventHandlerTest {

  @Mock private SettlementService settlementService;
  @Mock private PointReservationService pointReservationService;

  private SettlementEventHandler settlementEventHandler;

  @BeforeEach
  void setUp() {
    settlementEventHandler = new SettlementEventHandler(settlementService, pointReservationService);
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
    then(pointReservationService).shouldHaveNoInteractions();
  }

  @Test
  void 유찰_이벤트를_받으면_포인트_예약_해제를_위임하고_정산하지_않는다() {
    // given
    AuctionEndedMessageQueueEvent event =
        createEndedEvent(1L, null, 3L, null, AuctionStatus.PASSED);

    // when
    settlementEventHandler.handle(event);

    // then
    then(pointReservationService).should().releaseForPassedAuction(1L);
    then(settlementService).shouldHaveNoInteractions();
  }

  @Test
  void 다른_인스턴스가_동시에_처리해_유니크_제약에_막혀도_예외를_다시_던지지_않는다() {
    // given: 다른 인스턴스가 먼저 커밋해 이 인스턴스의 정산 트랜잭션은 유니크 제약에 막혀 롤백된 상황을 흉내낸다
    AuctionEndedMessageQueueEvent event = createEndedEvent(1L, 2L, 3L, 10_500L);
    willThrow(dataIntegrityViolation("uk_settlement_auction_member_type"))
        .given(settlementService)
        .settleAuction(1L, 2L, 3L, 10_500L);

    // when & then: 메시지를 정상 소비 처리할 수 있도록 예외가 밖으로 새어 나가지 않아야 한다
    assertThatCode(() -> settlementEventHandler.handle(event)).doesNotThrowAnyException();
  }

  @Test
  void 다른_유니크_제약에_막히면_예외를_다시_던진다() {
    // given
    AuctionEndedMessageQueueEvent event = createEndedEvent(1L, 2L, 3L, 10_500L);
    DataIntegrityViolationException exception =
        dataIntegrityViolation("uk_point_transaction_idempotency_key");
    willThrow(exception).given(settlementService).settleAuction(1L, 2L, 3L, 10_500L);

    // when & then
    assertThatThrownBy(() -> settlementEventHandler.handle(event)).isSameAs(exception);
  }

  @Test
  void 제약_이름을_확인할_수_없는_무결성_예외면_다시_던진다() {
    // given
    AuctionEndedMessageQueueEvent event = createEndedEvent(1L, 2L, 3L, 10_500L);
    DataIntegrityViolationException exception = new DataIntegrityViolationException("unknown");
    willThrow(exception).given(settlementService).settleAuction(1L, 2L, 3L, 10_500L);

    // when & then
    assertThatThrownBy(() -> settlementEventHandler.handle(event)).isSameAs(exception);
  }

  private DataIntegrityViolationException dataIntegrityViolation(String constraintName) {
    ConstraintViolationException cause =
        new ConstraintViolationException(
            "constraint violation",
            new SQLException("duplicate key"),
            "insert into settlement ...",
            constraintName);
    return new DataIntegrityViolationException("정산 저장 중 무결성 제약 위반", cause);
  }

  private AuctionEndedMessageQueueEvent createEndedEvent(
      Long auctionId, Long winnerMemberId, Long sellerMemberId, Long winningPrice) {
    return createEndedEvent(
        auctionId, winnerMemberId, sellerMemberId, winningPrice, AuctionStatus.WON);
  }

  private AuctionEndedMessageQueueEvent createEndedEvent(
      Long auctionId,
      Long winnerMemberId,
      Long sellerMemberId,
      Long winningPrice,
      AuctionStatus auctionStatus) {
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
        auctionStatus,
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now(),
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now());
  }
}
