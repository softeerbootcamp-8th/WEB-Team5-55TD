package com.ootd.pickup.auction.scheduler;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 경매 스케줄러 전용 영속성. MVC 계층의 {@code AuctionRepository}와 {@link Auction} 엔티티만 공유한다.
 *
 * <p>{@code JpaRepository}가 아니라 {@link Repository}를 상속하는 이유는 여기 선언한 메서드만 존재하게 하기 위해서다. {@code
 * save}나 {@code findAll}이 노출되지 않아 경매를 한 건씩 저장하는 경로가 타입 차원에서 막힌다. 상태 전이는 반드시 bulk update로만 일어난다.
 *
 * <p>전이 메서드는 도착 상태를 파라미터로 받지 않고 이름에 박아 둔다. 낙찰과 유찰은 조건이 서로 다르므로, 상태를 넘길 수 있게 하면 조건과 도착 상태가 어긋난 조합을
 * 호출할 수 있다.
 */
public interface AuctionSchedulerJpaRepository extends Repository<Auction, Long> {

  /**
   * 시작 시각에 도달한 경매의 식별자를 조회한다.
   *
   * <p>엔티티가 아니라 식별자만 읽는다. 엔티티를 영속성 컨텍스트에 올리면 뒤이은 bulk update와 상태가 어긋난다.
   *
   * @param auctionStatus 전이 전 상태
   * @param baseTime 이 시각까지 시작된 경매를 대상으로 한다
   * @param limit 한 번에 처리할 최대 건수
   * @return 오래된 경매부터 정렬된 식별자 목록. 대상이 없으면 빈 목록
   */
  @Query(
      """
      select auction.auctionId
      from Auction auction
      where auction.auctionStatus = :auctionStatus
        and auction.startedAt <= :baseTime
      order by auction.auctionId asc
      """)
  List<Long> findAllIdsByAuctionStatusAndStartedAtLessThanEqual(
      @Param("auctionStatus") AuctionStatus auctionStatus,
      @Param("baseTime") LocalDateTime baseTime,
      Limit limit);

  /**
   * 종료 시각에 도달한 경매의 식별자를 조회한다.
   *
   * <p>{@code endedAt}이 null인 경매는 비교식이 참이 되지 않아 자연히 제외된다.
   *
   * @param auctionStatus 전이 전 상태
   * @param baseTime 이 시각까지 종료된 경매를 대상으로 한다
   * @param limit 한 번에 처리할 최대 건수
   * @return 오래된 경매부터 정렬된 식별자 목록. 대상이 없으면 빈 목록
   */
  @Query(
      """
      select auction.auctionId
      from Auction auction
      where auction.auctionStatus = :auctionStatus
        and auction.endedAt <= :baseTime
      order by auction.auctionId asc
      """)
  List<Long> findAllIdsByAuctionStatusAndEndedAtLessThanEqual(
      @Param("auctionStatus") AuctionStatus auctionStatus,
      @Param("baseTime") LocalDateTime baseTime,
      Limit limit);

  /**
   * 예정 상태인 경매를 진행 중으로 전이시킨다.
   *
   * <p>{@code auction_status}만 갱신하는 것이 핵심이다. 엔티티를 읽어 수정하면 JPA 변경 감지가 행 전체를 쓰기 때문에, 마감 직전 입찰이 갱신한
   * {@code winning_price}를 덮어 유실시킨다. bulk update는 그 컬럼을 건드리지 않으므로 입찰과 경쟁하지 않고 비관적 락도 필요 없다.
   *
   * <p>{@code SCHEDULED} 조건은 조회와 갱신 사이에 상태가 바뀐 경매를 제외한다.
   *
   * @param auctionIds 전이 대상 식별자 목록
   * @return 실제로 갱신된 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Auction auction
      set auction.auctionStatus = com.ootd.pickup.auction.domain.AuctionStatus.ONGOING
      where auction.auctionId in :auctionIds
        and auction.auctionStatus = com.ootd.pickup.auction.domain.AuctionStatus.SCHEDULED
      """)
  int updateAuctionStatusToOngoingByIdIn(@Param("auctionIds") List<Long> auctionIds);

  /**
   * 리저브를 채운 경매를 낙찰로 전이시킨다.
   *
   * <p>리저브 비교를 DB에서 한다. 엔티티를 로드해 비교하면 {@code winning_price}를 읽은 시점과 갱신 시점이 벌어져, 그 사이에 들어온 마감 직전 입찰이
   * 판정에 반영되지 않는다.
   *
   * @param auctionIds 전이 대상 식별자 목록
   * @return 낙찰로 갱신된 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Auction auction
      set auction.auctionStatus = com.ootd.pickup.auction.domain.AuctionStatus.WON
      where auction.auctionId in :auctionIds
        and auction.auctionStatus = com.ootd.pickup.auction.domain.AuctionStatus.ONGOING
        and auction.winningPrice is not null
        and auction.winningPrice >= auction.reservePrice
      """)
  int updateAuctionStatusToWonByIdIn(@Param("auctionIds") List<Long> auctionIds);

  /**
   * 리저브를 채우지 못한 경매를 유찰로 전이시킨다.
   *
   * <p>입찰이 없어 {@code winning_price}가 null인 경우도 유찰이다. {@link #updateAuctionStatusToWonByIdIn}의 조건과
   * 배타적이면서 둘을 합치면 모든 경우를 덮으므로, 두 건수의 합이 대상 수와 같아야 한다.
   *
   * @param auctionIds 전이 대상 식별자 목록
   * @return 유찰로 갱신된 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Auction auction
      set auction.auctionStatus = com.ootd.pickup.auction.domain.AuctionStatus.PASSED
      where auction.auctionId in :auctionIds
        and auction.auctionStatus = com.ootd.pickup.auction.domain.AuctionStatus.ONGOING
        and (auction.winningPrice is null or auction.winningPrice < auction.reservePrice)
      """)
  int updateAuctionStatusToPassedByIdIn(@Param("auctionIds") List<Long> auctionIds);

  /**
   * 이벤트 조립에 필요한 연관까지 함께 경매를 조회한다.
   *
   * <p>종료 이벤트가 판매자 식별자를 담아야 하는데, 소비자는 트랜잭션 밖에 실행되므로 지연 로딩이 실패한다. 발행하는 쪽에서 미리 채워야 하므로 fetch join으로
   * 함께 읽는다.
   *
   * <p><b>상태 전이 뒤에 호출해야 한다.</b> 같은 트랜잭션이라 갱신된 상태가 보이고, 이벤트에 실리는 {@code auctionStatus}가 낙찰/유찰로 확정된
   * 값이 된다.
   *
   * @param auctionIds 조회할 경매 식별자 목록
   * @return 위탁 상품과 판매자가 함께 로드된 경매 목록. 순서는 보장하지 않는다
   */
  @Query(
      """
      select auction
      from Auction auction
      join fetch auction.consignment consignment
      join fetch consignment.sellerMember
      where auction.auctionId in :auctionIds
      """)
  List<Auction> findAllWithConsignmentAndSellerMemberByIdIn(
      @Param("auctionIds") List<Long> auctionIds);

  /**
   * 입찰을 입찰자와 함께 조회한다.
   *
   * <p>낙찰 이벤트에 낙찰자 식별자를 담기 위한 것이다. 경매의 {@code winningBidId}로 찾으며, 유찰된 경매는 대상이 없다.
   *
   * <p>{@code Repository<Auction, Long>}를 상속하지만 JPQL 이 대상으로 삼는 엔티티는 자유롭게 고를 수 있다.
   *
   * @param bidIds 조회할 입찰 식별자 목록
   * @return 입찰자가 함께 로드된 입찰 목록
   */
  @Query(
      """
      select bid
      from Bid bid
      join fetch bid.member
      where bid.bidId in :bidIds
      """)
  List<Bid> findAllBidsWithMemberByIdIn(@Param("bidIds") List<Long> bidIds);
}
