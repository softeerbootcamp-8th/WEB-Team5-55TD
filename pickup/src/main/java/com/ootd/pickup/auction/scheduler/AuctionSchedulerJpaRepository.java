package com.ootd.pickup.auction.scheduler;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 경매 스케줄러의 대상 조회. 상태 전이(bulk update)는 {@link AuctionSchedulerRepository}가 QueryDSL로 처리한다.
 *
 * <p>{@code JpaRepository}가 아니라 {@link Repository}를 상속해 여기 선언한 메서드만 존재하게 한다.
 */
public interface AuctionSchedulerJpaRepository extends Repository<Auction, Long> {

  /**
   * 시작 시각에 도달한 경매의 식별자를 조회한다.
   *
   * <p>엔티티가 아니라 식별자만 읽는다. 엔티티를 영속성 컨텍스트에 올리면 뒤이은 bulk update와 상태가 어긋난다.
   *
   * <p>기준 시각을 파라미터로 받지 않고 {@code local datetime}으로 DB에서 읽는다. 인스턴스마다 시계가 어긋나면 어느 인스턴스가 이 작업을 잡았는지에
   * 따라 전이 대상이 갈라진다.
   *
   * @param auctionStatus 전이 전 상태
   * @param limit 한 번에 처리할 최대 건수
   * @return 시작 시각이 이른 경매부터 정렬된 식별자 목록. 대상이 없으면 빈 목록
   */
  @Query(
      """
      select auction.auctionId
      from Auction auction
      where auction.auctionStatus = :auctionStatus
        and auction.startedAt <= local datetime
      order by auction.startedAt asc, auction.auctionId asc
      """)
  List<Long> findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow(
      @Param("auctionStatus") AuctionStatus auctionStatus, Limit limit);

  /**
   * 종료 시각에 도달한 경매의 식별자를 조회한다.
   *
   * <p>{@code endedAt}이 null인 경매는 비교식이 참이 되지 않아 자연히 제외된다.
   *
   * @param auctionStatus 전이 전 상태
   * @param limit 한 번에 처리할 최대 건수
   * @return 종료 시각이 이른 경매부터, 즉 가장 오래 밀린 경매부터 정렬된 식별자 목록. 대상이 없으면 빈 목록
   */
  @Query(
      """
      select auction.auctionId
      from Auction auction
      where auction.auctionStatus = :auctionStatus
        and auction.endedAt <= local datetime
      order by auction.endedAt asc, auction.auctionId asc
      """)
  List<Long> findAllIdsByAuctionStatusAndEndedAtLessThanEqualNow(
      @Param("auctionStatus") AuctionStatus auctionStatus, Limit limit);
}
