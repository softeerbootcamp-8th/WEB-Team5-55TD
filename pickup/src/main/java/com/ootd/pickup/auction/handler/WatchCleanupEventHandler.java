package com.ootd.pickup.auction.handler;

import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.auction.service.WatchService;
import com.ootd.pickup.global.event.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link AuctionEndedMessageQueueEvent} 수신 어댑터.
 *
 * <p>종료된 경매는 더 이상 예정 상태가 아니므로 회원이 저장해 둔 관심(Watch)이 의미를 잃는다. 삭제 대상은 {@code auctionId}만으로 정해지므로
 * {@link WatchService#deleteWatchesByAuctionId}는 몇 번 실행해도 결과가 같다({@code delete}는 대상이 이미 없으면 0건 처리로
 * 끝난다). 재전달로 여러 번 호출돼도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchCleanupEventHandler implements EventHandler<AuctionEndedMessageQueueEvent> {

  private final WatchService watchService;

  @Override
  public Class<AuctionEndedMessageQueueEvent> eventClass() {
    return AuctionEndedMessageQueueEvent.class;
  }

  @Override
  public void handle(AuctionEndedMessageQueueEvent event) {
    watchService.deleteWatchesByAuctionId(event.auctionId());
    log.info("경매 종료로 관심을 삭제했습니다 - auctionId={}", event.auctionId());
  }
}
