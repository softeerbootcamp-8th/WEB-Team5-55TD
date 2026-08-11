package com.ootd.pickup.auction.scheduler;

import static com.ootd.pickup.auction.domain.AuctionStatus.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.event.AuctionEndedMessageQueueEvent;
import com.ootd.pickup.auction.event.AuctionEndedNotificationEvent;
import com.ootd.pickup.auction.event.AuctionStartedNotificationEvent;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.EventPublisher;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경매 상태 전이를 실행한다. 전이 하나가 트랜잭션 하나다.
 *
 * <p>전이 결과는 필요한 보장이 달라 두 경로로 알린다.
 *
 * <ul>
 *   <li>{@link AuctionEndedMessageQueueEvent} — 정산이 정확히 한 번 돌아야 하므로 상태 전이와 같은 커밋에 Outbox로 적재한다.
 *   <li>{@link AuctionStartedNotificationEvent} — 화면 갱신용이라 유실이 허용된다. 커밋 이후 발행되는 것은 {@link
 *       EventPublisher}가 보장하므로, 이벤트를 만들어 넘기기만 한다.
 * </ul>
 *
 * <p>이벤트는 트랜잭션 <b>안에서</b> 만든다. {@code fromEntity}가 위탁 상품을 지연 로딩으로 타고 들어간다.
 *
 * <p><b>전이 메서드는 다른 빈에서 호출해야 한다.</b> 같은 클래스 안에서 부르면 프록시를 지나지 않아 트랜잭션이 열리지 않는다. 그 상태로 진행되면 {@link
 * EventProducer#produce}가 진행 중인 트랜잭션을 요구하므로 적재 시점에 실패한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionStatusTransitionService {

  /**
   * 한 번에 처리할 최대 경매 수.
   *
   * <p>장기간 중단 후 재개하면 밀린 경매가 수천 건일 수 있다. 상한이 없으면 한 트랜잭션이 그만큼 커지고 잠금 점유 시간도 길어진다. 남은 건은 다음 주기가 가져간다.
   */
  private static final Limit BATCH_LIMIT = Limit.of(100);

  private final AuctionSchedulerRepository auctionSchedulerJpaRepository;
  private final AuctionSchedulerStatusUpdateRepository auctionSchedulerStatusUpdateRepository;
  private final EventProducer eventProducer;
  private final EventPublisher eventPublisher;

  /** 시작 시각에 도달한 예정 경매를 진행 중으로 전이시킨다. 대상 판정 기준 시각은 DB에서 읽는다. */
  @Transactional
  public void startDueAuctions() {
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow(
            SCHEDULED, BATCH_LIMIT);
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

    publishStarted(auctionIds);
    log.info("경매를 시작했습니다 - count={}, auctionIds={}", updated, auctionIds);
  }

  private void publishStarted(List<Long> auctionIds) {
    auctionSchedulerJpaRepository.findAllWithConsignmentAndSellerMemberByIdIn(auctionIds).stream()
        .filter(auction -> auction.getAuctionStatus() == ONGOING)
        .map(AuctionStartedNotificationEvent::fromEntity)
        .forEach(eventPublisher::publish);
  }

  /**
   * 종료 시각에 도달한 경매를 낙찰과 유찰로 갈라 전이시키고, 종료 이벤트를 Outbox에 적재한다.
   *
   * <p>낙찰과 유찰은 도착 상태가 달라 한 문장으로 처리할 수 없다. 리저브 조건이 서로 배타적이고 둘을 합치면 모든 경우를 덮으므로, 두 문장으로 나눠도 빠지거나 겹치는
   * 경매가 없다.
   *
   * <p>적재가 전이와 같은 트랜잭션에 들어가는 것이 Outbox 패턴의 전부다. 커밋 직후 프로세스가 죽어도 "상태는 종료됐는데 정산 이벤트는 없는" 상태가 생기지 않는다.
   *
   * <p>대상 판정 기준 시각은 DB에서 읽는다.
   */
  @Transactional
  public void endDueAuctions() {
    List<Long> auctionIds =
        auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndEndedAtLessThanEqualNow(
            ONGOING, BATCH_LIMIT);
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

    auctionSchedulerStatusUpdateRepository.updateConsignmentStatusToSoldByAuctionIdIn(auctionIds);
    auctionSchedulerStatusUpdateRepository.updateConsignmentStatusToRegisterableByAuctionIdIn(
        auctionIds);

    List<Auction> closedAuctions = findClosedAuctions(auctionIds);
    Map<Long, Bid> winningBidsById = findWinningBidsById(closedAuctions);

    int appended = appendClosedEventsToOutbox(closedAuctions, winningBidsById);
    closedAuctions.stream()
        .map(
            auction ->
                AuctionEndedNotificationEvent.fromEntity(
                    auction, winningBidOf(auction, winningBidsById)))
        .forEach(eventPublisher::publish);

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
   * <p>전이 뒤에 다시 읽는 이유는 이벤트에 담을 값이 식별자만으로는 부족하기 때문이다. 소비자는 다른 프로세스에서 트랜잭션 밖에 실행되므로 판매자·낙찰자를 스스로 조회할
   * 수 없고, 발행하는 쪽이 미리 채워 보내야 한다.
   *
   * <p>종료 상태로 한 번 더 거른다. 전이 건수가 대상과 어긋나 갱신되지 않은 경매가 섞이면, 아직 진행 중인 경매의 종료 이벤트를 발행하게 된다.
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
    if (auction.getAuctionStatus() != WON) {
      return null;
    }
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
