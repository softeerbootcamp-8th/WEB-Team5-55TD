package com.ootd.pickup.point.service;

import static com.ootd.pickup.global.exception.ExceptionCode.INSUFFICIENT_BID_LIMIT;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.domain.PointReservation;
import com.ootd.pickup.point.domain.PointReservationStatus;
import com.ootd.pickup.point.repository.PointRepository;
import com.ootd.pickup.point.repository.PointReservationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointReservationService {

  private final PointRepository pointRepository;
  private final PointReservationRepository pointReservationRepository;
  private final PointLockService pointLockService;

  /**
   * 입찰 예약에 필요한 {@code PointReservation}/{@code Point} 행을 잠그고 잔액이 충분한지 검증한다.
   *
   * <p>{@link #reserveHighestBid}에서 같은 행을 다시 잠그지 않도록, 여기서 잠근 엔티티를 {@link PreparedBidReservation}에
   * 담아 반환한다. 잔액 검증을 {@code Bid} insert 이전에 끝냄으로써 잔액 부족으로 실패하는 입찰이 불필요한 insert-then-rollback을 하지 않게
   * 한다.
   */
  @Transactional
  public PreparedBidReservation prepareReservation(Auction auction, Member bidder, long amount) {
    PointReservation reservation =
        pointReservationRepository.findByAuctionIdForUpdate(auction.getAuctionId()).orElse(null);
    List<Long> memberIds = new ArrayList<>();
    memberIds.add(bidder.getMemberId());
    if (reservation != null
        && reservation.getReservationStatus() == PointReservationStatus.ACTIVE) {
      memberIds.add(reservation.getMember().getMemberId());
    }
    Map<Long, Point> points = pointLockService.lockPoints(memberIds);
    Point bidderPoint = points.get(bidder.getMemberId());
    long reusableReservation =
        reservation != null
                && reservation.getReservationStatus() == PointReservationStatus.ACTIVE
                && reservation.getMember().getMemberId().equals(bidder.getMemberId())
            ? reservation.getAmount()
            : 0L;
    log.debug(
        "포인트 예약 가능 여부 확인 - memberId={}, requestedAmount={}, availableBalance={}, reusableReservation={}",
        bidder.getMemberId(),
        amount,
        bidderPoint.getAvailableBalance(),
        reusableReservation);
    if (amount > Math.addExact(bidderPoint.getAvailableBalance(), reusableReservation)) {
      throw new PickUpException(INSUFFICIENT_BID_LIMIT);
    }
    return new PreparedBidReservation(reservation, points);
  }

  @Transactional
  public void reserveHighestBid(
      Auction auction, PreparedBidReservation prepared, Bid bid, Member bidder) {
    PointReservation reservation = prepared.reservation();
    Point bidderPoint = prepared.lockedPoints().get(bidder.getMemberId());
    if (reservation == null) {
      reserve(bidderPoint, bid.getBidPrice());
      pointReservationRepository.save(
          PointReservation.create(auction, bid, bidder, bid.getBidPrice()));
    } else {
      if (reservation.getReservationStatus() != PointReservationStatus.ACTIVE) {
        throw new IllegalStateException("종료된 포인트 예약이 진행 중 경매에 남아 있습니다.");
      }
      Point previousPoint = prepared.lockedPoints().get(reservation.getMember().getMemberId());
      previousPoint.release(reservation.getAmount());
      reserve(bidderPoint, bid.getBidPrice());
      pointRepository.save(previousPoint);
      reservation.replace(bid, bidder, bid.getBidPrice());
      pointReservationRepository.save(reservation);
    }
    pointRepository.save(bidderPoint);
    auction.markBidReserved();
  }

  @Transactional
  public void releaseForPassedAuction(Long auctionId) {
    pointReservationRepository
        .findByAuctionIdForUpdate(auctionId)
        .filter(reservation -> reservation.getReservationStatus() == PointReservationStatus.ACTIVE)
        .ifPresent(
            reservation -> {
              Point point =
                  pointLockService.getPointForUpdate(reservation.getMember().getMemberId());
              point.release(reservation.getAmount());
              reservation.release();
              pointRepository.save(point);
              pointReservationRepository.save(reservation);
              log.info(
                  "유찰된 경매의 포인트 예약을 해제했습니다 - auctionId={}, memberId={}, amount={}",
                  auctionId,
                  reservation.getMember().getMemberId(),
                  reservation.getAmount());
            });
  }

  private void reserve(Point point, long amount) {
    if (amount > point.getAvailableBalance()) {
      throw new PickUpException(INSUFFICIENT_BID_LIMIT);
    }
    point.reserve(amount);
  }

  /**
   * {@link #prepareReservation}에서 잠근 {@code PointReservation}/{@code Point}를 {@link
   * #reserveHighestBid}로 넘기기 위한 락 컨텍스트.
   */
  public record PreparedBidReservation(
      PointReservation reservation, Map<Long, Point> lockedPoints) {}
}
