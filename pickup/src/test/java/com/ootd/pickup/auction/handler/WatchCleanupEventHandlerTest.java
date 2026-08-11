package com.ootd.pickup.auction.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.auction.service.WatchService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WatchCleanupEventHandlerTest {

  @Mock private WatchService watchService;

  private WatchCleanupEventHandler watchCleanupEventHandler;

  @BeforeEach
  void setUp() {
    watchCleanupEventHandler = new WatchCleanupEventHandler(watchService);
  }

  @Test
  void eventClass를_호출하면_AuctionEndedMessageQueueEvent를_반환한다() {
    // when & then
    assertThat(watchCleanupEventHandler.eventClass())
        .isEqualTo(AuctionEndedMessageQueueEvent.class);
  }

  @Test
  void 이벤트를_받으면_경매의_관심을_모두_삭제한다() {
    // given
    AuctionEndedMessageQueueEvent event = createEndedEvent(100L);

    // when
    watchCleanupEventHandler.handle(event);

    // then
    then(watchService).should().deleteWatchesByAuctionId(100L);
  }

  private AuctionEndedMessageQueueEvent createEndedEvent(Long auctionId) {
    return new AuctionEndedMessageQueueEvent(
        "event-id",
        auctionId,
        200L,
        1L,
        10_000L,
        10_000L,
        10L,
        2L,
        12_000L,
        AuctionStatus.WON,
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now(),
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now());
  }
}
