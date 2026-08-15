package com.ootd.pickup.auction.cache;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.ootd.pickup.auction.cache.event.AuctionSnapshotChangedEvent;
import com.ootd.pickup.auction.domain.AuctionStatus;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AuctionSnapshotCacheEventListenerTest {

  private final AuctionSnapshotCache auctionSnapshotCache = mock(AuctionSnapshotCache.class);
  private final AtomicReference<Runnable> submittedTask = new AtomicReference<>();
  private final Executor executor = submittedTask::set;
  private final AuctionSnapshotCacheEventListener listener =
      new AuctionSnapshotCacheEventListener(auctionSnapshotCache, executor);

  @Test
  void 커밋_리스너는_캐시_갱신을_executor에_위임한다() {
    AuctionSnapshotChangedEvent event = testEvent();

    listener.onAuctionSnapshotChanged(event);

    then(auctionSnapshotCache).shouldHaveNoInteractions();

    submittedTask.get().run();

    then(auctionSnapshotCache).should().put(event.toSnapshot());
  }

  @Test
  void executor가_작업을_거부해도_호출자에게_예외를_전파하지_않는다() {
    Executor rejectingExecutor =
        command -> {
          throw new RejectedExecutionException("queue full");
        };
    AuctionSnapshotCacheEventListener rejectingListener =
        new AuctionSnapshotCacheEventListener(auctionSnapshotCache, rejectingExecutor);

    Assertions.assertThatCode(() -> rejectingListener.onAuctionSnapshotChanged(testEvent()))
        .doesNotThrowAnyException();
    then(auctionSnapshotCache).shouldHaveNoInteractions();
  }

  private AuctionSnapshotChangedEvent testEvent() {
    return new AuctionSnapshotChangedEvent(
        1L, 10_000L, 500L, AuctionStatus.ONGOING, LocalDateTime.now().plusHours(1), 9L);
  }
}
