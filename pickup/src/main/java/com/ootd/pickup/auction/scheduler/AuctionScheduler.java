package com.ootd.pickup.auction.scheduler;

import com.ootd.pickup.auction.service.AuctionStatusTransitionService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 경매를 시각에 맞춰 다음 상태로 넘긴다.
 *
 * <p>경매 상태는 사용자 요청이 아니라 시간이 바꾼다. 시작 시각이 지나면 입찰이 열려야 하고 종료 시각이 지나면 닫혀야 하는데 그 순간에 요청을 보내주는 사용자가 없어,
 * 주기적으로 확인하는 주체가 필요하다. 여러 인스턴스가 떠 있어도 한 번만 돌아야 하므로 {@link SchedulerLock}으로 실행권을 나눈다.
 *
 * <p>전이 자체는 {@link AuctionStatusTransitionService}가 한다. 이 클래스에는 <b>트랜잭션을 걸지 않는다.</b> 걸면 두 전이가 한
 * 트랜잭션으로 합쳐져, 먼저 끝난 시작 전이가 종료 전이가 끝날 때까지 경매 행의 잠금을 붙들고 있게 된다. 그 경매는 방금 입찰이 열린 경매라 대기가 그대로 입찰 지연이
 * 된다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "scheduler.auction.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuctionScheduler {

  private final AuctionStatusTransitionService auctionStatusTransitionService;

  /**
   * 시작·종료 시각에 도달한 경매의 상태를 전이시킨다.
   *
   * <p>두 전이는 각각 별개의 트랜잭션에서 돈다. 전이 대상을 처리 이력이 아니라 <b>현재 상태로 조회</b>하므로, 한쪽이 실패해도 다음 주기가 실패한 쪽만 다시
   * 집어간다. 이미 전이된 경매는 조회 조건에 걸리지 않아 중복 처리도 없다.
   *
   * <p>기준 시각은 각 전이의 조회 쿼리가 DB에서 직접 읽는다. 두 전이가 보는 시각이 수 밀리초 어긋나지만, 대상 판정은 어차피 다음 주기가 이어받으므로 문제가 되지
   * 않는다. 인스턴스 시계를 쓰지 않는 것이 더 중요하다.
   *
   * <p>시작 전이가 먼저라, 시작 시각과 종료 시각이 모두 지난 경매(예: 장기간 중단 후 재개)는 한 주기에 {@code SCHEDULED → ONGOING →
   * WON/PASSED}까지 간다.
   */
  @Scheduled(fixedDelayString = "${scheduler.auction.fixed-delay:1s}")
  @SchedulerLock(
      name = "auction-status-transition",
      lockAtMostFor = "PT30S",
      // 주기가 1초라 인스턴스 간 시계 오차만으로 두 번 연달아 실행될 수 있다. 주기보다 짧게 잡아야
      // 정상 주기가 잠금에 막혀 건너뛰지 않는다.
      lockAtLeastFor = "PT0.5S")
  public void transitionDueAuctions() {
    auctionStatusTransitionService.startDueAuctions();
    auctionStatusTransitionService.endDueAuctions();
  }
}
