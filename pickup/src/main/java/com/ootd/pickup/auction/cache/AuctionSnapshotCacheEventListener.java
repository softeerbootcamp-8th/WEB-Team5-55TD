package com.ootd.pickup.auction.cache;

import com.ootd.pickup.auction.cache.event.AuctionSnapshotChangedEvent;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link AuctionSnapshotChangedEvent}를 커밋 이후 {@link AuctionSnapshotCache}에 반영하는 다리.
 *
 * <p>{@link com.ootd.pickup.global.event.notification.NotificationEventListener}와 같은 패턴이다 — 같은
 * {@code notificationEventExecutor}로 실행을 넘겨, 커밋 이후에도 SQS 소비 스레드나 스케줄러 트랜잭션 스레드가 Redis 쓰기로 지연되지 않게
 * 한다. 실행 큐가 가득 차면 그 갱신은 건너뛰고 로그만 남긴다 — 스냅샷 캐시는 밑져야 본전인 사전 필터용이라 유실이 허용된다.
 *
 * <p>{@code fallbackExecution = true}라 트랜잭션이 없으면 즉시 실행된다.
 */
@Slf4j
@Component
public class AuctionSnapshotCacheEventListener {

  private final AuctionSnapshotCache auctionSnapshotCache;
  private final Executor notificationEventExecutor;

  public AuctionSnapshotCacheEventListener(
      AuctionSnapshotCache auctionSnapshotCache,
      @Qualifier("notificationEventExecutor") Executor notificationEventExecutor) {
    this.auctionSnapshotCache = auctionSnapshotCache;
    this.notificationEventExecutor = notificationEventExecutor;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onAuctionSnapshotChanged(AuctionSnapshotChangedEvent event) {
    try {
      notificationEventExecutor.execute(() -> auctionSnapshotCache.put(event.toSnapshot()));
    } catch (RejectedExecutionException exception) {
      log.warn("경매 스냅샷 캐시 갱신 실행 큐가 가득 찼습니다 - auctionId={}", event.auctionId(), exception);
    }
  }
}
