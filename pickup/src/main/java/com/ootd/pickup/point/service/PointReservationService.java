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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointReservationService {

  private final PointRepository pointRepository;
  private final PointReservationRepository pointReservationRepository;
  private final PointLockService pointLockService;

  @Transactional
  public void validateAvailable(Auction auction, Member bidder, long amount) {
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
    if (amount > Math.addExact(bidderPoint.getAvailableBalance(), reusableReservation)) {
      throw new PickUpException(INSUFFICIENT_BID_LIMIT);
    }
  }

  @Transactional
  public void reserveHighestBid(Auction auction, Bid bid, Member bidder) {
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
    if (reservation == null) {
      reserve(bidderPoint, bid.getBidPrice());
      pointReservationRepository.save(
          PointReservation.create(auction, bid, bidder, bid.getBidPrice()));
    } else {
      if (reservation.getReservationStatus() != PointReservationStatus.ACTIVE) {
        throw new IllegalStateException("종료된 포인트 예약이 진행 중 경매에 남아 있습니다.");
      }
      Point previousPoint = points.get(reservation.getMember().getMemberId());
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
            });
  }

  private void reserve(Point point, long amount) {
    if (amount > point.getAvailableBalance()) {
      throw new PickUpException(INSUFFICIENT_BID_LIMIT);
    }
    point.reserve(amount);
  }
}
