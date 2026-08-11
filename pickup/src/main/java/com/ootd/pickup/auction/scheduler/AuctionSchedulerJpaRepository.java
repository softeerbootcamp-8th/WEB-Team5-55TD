package com.ootd.pickup.auction.scheduler;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 경매 스케줄러 전용 영속성. MVC 계층의 {@code AuctionRepository}와 {@link Auction} 엔티티만 공유한다.
 *
 * <p>{@code JpaRepository}가 아니라 {@link Repository}를 상속하는 이유는 여기 선언한 메서드만 존재하게 하기 위해서다. {@code
 * save}나 {@code findAll}이 노출되지 않아 경매를 한 건씩 저장하는 경로가 타입 차원에서 막힌다. 상태 전이는 반드시 bulk update로만 일어난다.
 *
 * <p>조회만 이 인터페이스가 담당하고, bulk update는 {@link AuctionSchedulerRepository}가 QueryDSL로 직접 실행한다 —
 * {@code winningPrice}·{@code reservePrice} 비교를 조건에 넣으려면 JPQL 문자열보다 타입 안전한 QueryDSL이 낫다.
 */
public interface AuctionSchedulerJpaRepository extends Repository<Auction, Long> {

  /**
   * 시작 시각에 도달한 경매의 식별자를 조회한다.
   *
   * <p>엔티티가 아니라 식별자만 읽는다. 엔티티를 영속성 컨텍스트에 올리면 뒤이은 bulk update와 상태가 어긋난다.
   *
   * <p>기준 시각을 파라미터로 받지 않고 {@code local datetime}으로 DB에서 읽는다. 인스턴스마다 시계가 어긋나면 어느 인스턴스가 이 작업을 잡았는지에
   * 따라 전이 대상이 갈라진다. DB가 시각의 <b>단일 진실원천</b>이다.
   *
   * <p><b>정렬 키를 식별자로 바꾸지 않는다.</b> 밀린 물량이 {@code limit}을 넘을 때 시작 시각이 이른 경매부터 처리해야 한다. 식별자는 등록 순서라 이
   * 우선순위를 지키지 못한다.
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
   * <p>기준 시각과 정렬 키는 {@link #findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow}와 같은 이유로 각각 DB
   * 시각과 {@code endedAt}이다.
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
