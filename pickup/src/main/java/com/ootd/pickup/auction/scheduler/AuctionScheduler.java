package com.ootd.pickup.auction.scheduler;

import static com.ootd.pickup.auction.domain.AuctionStatus.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.auction.event.AuctionEndedNotificationEvent;
import com.ootd.pickup.auction.event.AuctionStartedNotificationEvent;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.EventPublisher;
import com.ootd.pickup.global.event.NotificationEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 경매를 시각에 맞춰 다음 상태로 넘긴다.
 *
 * <p>경매 상태는 사용자 요청이 아니라 시간이 바꾼다. 시작 시각이 지나면 입찰이 열려야 하고 종료 시각이 지나면 닫혀야 하는데, 그 순간에 요청을 보내주는 사용자가 없기
 * 때문에 주기적으로 확인하는 주체가 필요하다.
 *
 * <p>웹 요청과는 별개의 진입점이라 MVC 계층의 서비스를 거치지 않고 {@link AuctionSchedulerJpaRepository}를 직접 쓴다. 그쪽에 스케줄러만
 * 쓰는 메서드가 쌓이지 않게 하고, 반대로 스케줄러가 서비스 변경에 끌려다니지 않게 한다.
 *
 * <p>여러 인스턴스가 떠 있어도 한 번만 돌아야 하므로 {@link SchedulerLock}으로 실행권을 나눈다.
 *
 * <p>전이 결과는 두 경로로 알린다. 계열이 갈린 이유는 필요한 보장이 다르기 때문이다.
 *
 * <ul>
 *   <li>{@link AuctionEndedMessageQueueEvent} — 정산이 정확히 한 번 돌아야 하므로 상태 전이와 같은 커밋에 Outbox로 적재한다.
 *   <li>{@link AuctionStartedNotificationEvent} — 화면 갱신용이라 유실이 허용된다. 커밋 이후 Redis로 즉시 발행한다.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "scheduler.auction.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuctionScheduler {

  /**
   * 한 번에 처리할 최대 경매 수.
   *
   * <p>상한을 두는 이유는 장기간 중단 후 재개했을 때다. 밀린 경매가 수천 건이면 한 트랜잭션이 그만큼 커지고 잠금 점유 시간도 길어진다. 남은 건은 다음 주기가
   * 가져간다.
   */
  private static final Limit BATCH_LIMIT = Limit.of(100);

  private final AuctionSchedulerJpaRepository auctionSchedulerJpaRepository;
  private final EventProducer eventProducer;
  private final EventPublisher eventPublisher;

  /**
   * 시작·종료 시각에 도달한 경매의 상태를 전이시킨다.
   *
   * <p>두 전이를 한 트랜잭션에 묶는다. 뒤쪽이 실패하면 앞쪽도 함께 되돌아가고 다음 주기가 둘 다 다시 시도하므로, 상태만 바뀌고 후속 처리는 누락되는 어긋남이 생기지
   * 않는다.
   *
   * <p>시작 전이가 먼저라, 시작 시각과 종료 시각이 모두 지난 경매(예: 장기간 중단 후 재개)는 한 주기에 {@code SCHEDULED → ONGOING →
   * WON/PASSED}까지 간다.
   *
   * <p>{@code @Transactional}과 함께 쓰는 것이 안전한 이유는 잠금 연산이 {@code PROPAGATION_REQUIRES_NEW}로 별도 커밋되기
   * 때문이다. 업무 로직이 롤백돼도 잠금 상태는 꼬이지 않는다.
   */
  @Scheduled(fixedDelayString = "${scheduler.auction.fixed-delay:1s}")
  @SchedulerLock(
      name = "auction-status-transition",
      lockAtMostFor = "PT30S",
      // 주기가 1초라 인스턴스 간 시계 오차만으로 두 번 연달아 실행될 수 있다. 주기보다 짧게 잡아야
      // 정상 주기가 잠금에 막혀 건너뛰지 않는다.
      lockAtLeastFor = "PT0.5S")
  @Transactional
  public void transitionDueAuctions() {
    LocalDateTime baseTime = LocalDateTime.now();
    startDueAuctions(baseTime);
    endDueAuctions(baseTime);
  }

  private void startDueAuctions(LocalDateTime baseTime) {
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndStartedAtLessThanEqual(
            SCHEDULED, baseTime, BATCH_LIMIT);
    if (auctionIds.isEmpty()) {
      return;
    }

    int updated = auctionSchedulerJpaRepository.updateAuctionStatusToOngoingByIdIn(auctionIds);
    if (updated != auctionIds.size()) {
      log.warn(
          "경매 시작 전이 건수가 대상과 다릅니다 - candidates={}, updated={}, auctionIds={}",
          auctionIds.size(),
          updated,
          auctionIds);
    }
    if (updated == 0) {
      return;
    }

    publishStartedAfterCommit(auctionIds);
    log.info("경매를 시작했습니다 - count={}, auctionIds={}", updated, auctionIds);
  }

  private void publishStartedAfterCommit(List<Long> auctionIds) {
    publishAfterCommit(
        "경매 시작 알림",
        auctionSchedulerJpaRepository
            .findAllWithConsignmentAndSellerMemberByIdIn(auctionIds)
            .stream()
            .filter(auction -> auction.getAuctionStatus() == ONGOING)
            .map(AuctionStartedNotificationEvent::fromEntity)
            .toList());
  }

  /**
   * 알림을 커밋 이후에 발행하도록 예약한다.
   *
   * <p>{@link EventPublisher}는 커밋 전에 보내면 안 된다. 롤백된 전이의 상태가 화면에 뜨고, 알림에는 그것을 되돌릴 방법이 없다. 그래서 전송 자체를
   * {@code afterCommit}으로 미룬다.
   *
   * <p>이벤트는 <b>호출 시점에</b> 만들어 넘겨야 한다. {@code fromEntity}가 위탁 상품을 타고 들어가므로, 생성을 커밋 이후로 미루면 영속성 컨텍스트가
   * 닫혀 지연 로딩이 실패한다. record는 식별자와 원시값만 담은 불변 객체라 트랜잭션 밖으로 넘겨도 안전하다.
   *
   * <p>발행 실패는 삼킨다. 알림은 유실이 허용되는 계열이고, 여기서 예외를 올리면 이미 커밋된 상태 전이가 실패한 것처럼 보인다.
   *
   * @param description 실패 로그에 쓸 알림 이름
   * @param events 커밋 이후 발행할 알림 목록
   */
  private void publishAfterCommit(String description, List<? extends NotificationEvent> events) {
    if (events.isEmpty()) {
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              events.forEach(eventPublisher::publish);
            } catch (RuntimeException exception) {
              log.warn("{} 발행에 실패했습니다 - count={}", description, events.size(), exception);
            }
          }
        });
  }

  /**
   * 종료 시각에 도달한 경매를 낙찰과 유찰로 갈라 전이시키고, 종료 이벤트를 Outbox에 적재한다.
   *
   * <p>낙찰과 유찰은 도착 상태가 다르므로 한 문장으로 처리할 수 없다. 리저브 조건이 서로 배타적이고 둘을 합치면 모든 경우를 덮기 때문에, 두 문장으로 나눠도 빠지거나
   * 겹치는 경매가 없다. 판정을 DB에 맡겨 경매를 읽어오지 않으므로 마감 직전 입찰까지 반영된다.
   *
   * <p>적재가 전이와 같은 트랜잭션에 들어가는 것이 Outbox 패턴의 전부다. 커밋 직후 프로세스가 죽어도 "상태는 종료됐는데 정산 이벤트는 없는" 상태가 생기지 않는다.
   */
  private void endDueAuctions(LocalDateTime baseTime) {
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndEndedAtLessThanEqual(
            ONGOING, baseTime, BATCH_LIMIT);
    if (auctionIds.isEmpty()) {
      return;
    }

    int won = auctionSchedulerJpaRepository.updateAuctionStatusToWonByIdIn(auctionIds);
    int passed = auctionSchedulerJpaRepository.updateAuctionStatusToPassedByIdIn(auctionIds);
    int updated = won + passed;

    if (updated != auctionIds.size()) {
      log.warn(
          "경매 종료 전이 건수가 대상과 다릅니다 - candidates={}, won={}, passed={}, auctionIds={}",
          auctionIds.size(),
          won,
          passed,
          auctionIds);
    }
    if (updated == 0) {
      return;
    }

    // 낙찰 입찰은 두 계열이 모두 필요로 한다. 한 번만 조회해 함께 쓴다.
    List<Auction> closedAuctions = findClosedAuctions(auctionIds);
    Map<Long, Bid> winningBidsById = findWinningBidsById(closedAuctions);

    int appended = appendClosedEventsToOutbox(closedAuctions, winningBidsById);
    publishAfterCommit(
        "경매 종료 알림",
        closedAuctions.stream()
            .map(
                auction ->
                    AuctionEndedNotificationEvent.fromEntity(
                        auction, winningBidOf(auction, winningBidsById)))
            .toList());

    log.info(
        "경매를 종료했습니다 - won={}, passed={}, outboxAppended={}, auctionIds={}",
        won,
        passed,
        appended,
        auctionIds);
  }

  /**
   * 전이가 끝난 경매를 이벤트 조립에 필요한 연관까지 함께 읽는다.
   *
   * <p>전이 뒤에 다시 읽는 이유는 이벤트에 담을 값이 식별자만으로는 부족하기 때문이다. 정산 소비자는 다른 프로세스에서 트랜잭션 밖에 실행되므로 판매자·낙찰자를 스스로
   * 조회할 수 없고, 발행하는 쪽이 미리 채워 보내야 한다.
   *
   * <p>종료 상태로 한 번 더 거르는 이유는 전이 건수가 대상과 어긋난 경우다. 갱신되지 않은 경매가 섞여 있으면 아직 진행 중인 경매의 종료 이벤트를 발행하게 된다.
   *
   * <p>한 번만 읽어 메시지 큐 계열과 알림 계열에 모두 쓴다. 같은 사건이므로 같은 시점의 값을 봐야 한다.
   *
   * @param auctionIds 전이를 시도한 경매 식별자 목록
   * @return 실제로 종료된 경매 목록
   */
  private List<Auction> findClosedAuctions(List<Long> auctionIds) {
    return auctionSchedulerJpaRepository
        .findAllWithConsignmentAndSellerMemberByIdIn(auctionIds)
        .stream()
        .filter(auction -> auction.getAuctionStatus().isTerminal())
        .toList();
  }

  /**
   * 종료된 경매의 메시지 큐 이벤트를 Outbox에 배치 적재한다.
   *
   * @param closedAuctions 전이가 끝난 경매 목록
   * @param winningBidsById 낙찰 입찰을 식별자로 찾을 수 있는 맵. 유찰된 경매는 대상이 없다
   * @return 적재한 이벤트 수
   */
  private int appendClosedEventsToOutbox(
      List<Auction> closedAuctions, Map<Long, Bid> winningBidsById) {
    if (closedAuctions.isEmpty()) {
      return 0;
    }

    List<AuctionEndedMessageQueueEvent> events =
        closedAuctions.stream()
            .map(
                auction ->
                    AuctionEndedMessageQueueEvent.fromEntity(
                        auction, winningBidOf(auction, winningBidsById)))
            .toList();

    events.forEach(eventProducer::produce);
    return events.size();
  }

  /** 유찰된 경매는 낙찰 입찰이 없다. 빈 맵에 null 키로 조회하면 예외가 나므로 먼저 걸러낸다. */
  private Bid winningBidOf(Auction auction, Map<Long, Bid> winningBidsById) {
    Long winningBidId = auction.getWinningBidId();
    return winningBidId == null ? null : winningBidsById.get(winningBidId);
  }

  private Map<Long, Bid> findWinningBidsById(List<Auction> closedAuctions) {
    List<Long> winningBidIds =
        closedAuctions.stream()
            .map(Auction::getWinningBidId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (winningBidIds.isEmpty()) {
      return Map.of();
    }

    return auctionSchedulerJpaRepository.findAllBidsWithMemberByIdIn(winningBidIds).stream()
        .collect(Collectors.toMap(Bid::getBidId, Function.identity()));
  }
}
