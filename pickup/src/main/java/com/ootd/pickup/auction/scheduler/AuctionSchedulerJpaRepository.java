package com.ootd.pickup.auction.scheduler;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
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
   * 경매가 진행 중으로 전이된 위탁 상품을 함께 진행 중으로 전이시킨다.
   *
   * <p>{@link #updateAuctionStatusToOngoingByIdIn}과 같은 이유로 위탁 상품도 bulk update로만 전이시킨다. 대상은 방금
   * 전이된 경매의 {@code consignmentId}로 좁힌다.
   *
   * @param auctionIds 방금 진행 중으로 전이된 경매 식별자 목록
   * @return 실제로 갱신된 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Consignment consignment
      set consignment.status = com.ootd.pickup.consignments.domain.ConsignmentStatus.AUCTION_ONGOING
      where consignment.consignmentId in (
        select auction.consignment.consignmentId
        from Auction auction
        where auction.auctionId in :auctionIds
      )
        and consignment.status = com.ootd.pickup.consignments.domain.ConsignmentStatus.AUCTION_SCHEDULED
      """)
  int updateConsignmentStatusToAuctionOngoingByAuctionIdIn(@Param("auctionIds") List<Long> auctionIds);

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
   * 낙찰로 전이된 경매의 위탁 상품을 판매 완료로 전이시킨다.
   *
   * <p>대상은 방금 갱신된 {@code auction_status}로 좁힌다. 이 메서드가 {@link #updateAuctionStatusToWonByIdIn} 바로
   * 다음에 불려야 정확한 대상을 잡는다.
   *
   * @param auctionIds 전이를 시도한 경매 식별자 목록
   * @return 실제로 갱신된 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Consignment consignment
      set consignment.status = com.ootd.pickup.consignments.domain.ConsignmentStatus.WON
      where consignment.consignmentId in (
        select auction.consignment.consignmentId
        from Auction auction
        where auction.auctionId in :auctionIds
          and auction.auctionStatus = com.ootd.pickup.auction.domain.AuctionStatus.WON
      )
        and consignment.status = com.ootd.pickup.consignments.domain.ConsignmentStatus.AUCTION_ONGOING
      """)
  int updateConsignmentStatusToWonByAuctionIdIn(@Param("auctionIds") List<Long> auctionIds);

  /**
   * 유찰로 전이된 경매의 위탁 상품을 재등록 가능(유찰) 상태로 전이시킨다.
   *
   * <p>{@code ConsignmentStatus.PASSED}는 재등록이 가능한 상태다({@code isModifiable}). 여기서 전이시키지 않으면 위탁
   * 상품이 {@code AUCTION_ONGOING}에 영원히 머물러, 유찰 후 같은 상품으로 경매를 다시 신청할 방법이 없다.
   *
   * @param auctionIds 전이를 시도한 경매 식별자 목록
   * @return 실제로 갱신된 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Consignment consignment
      set consignment.status = com.ootd.pickup.consignments.domain.ConsignmentStatus.PASSED
      where consignment.consignmentId in (
        select auction.consignment.consignmentId
        from Auction auction
        where auction.auctionId in :auctionIds
          and auction.auctionStatus = com.ootd.pickup.auction.domain.AuctionStatus.PASSED
      )
        and consignment.status = com.ootd.pickup.consignments.domain.ConsignmentStatus.AUCTION_ONGOING
      """)
  int updateConsignmentStatusToPassedByAuctionIdIn(@Param("auctionIds") List<Long> auctionIds);

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

  /**
   * 낙찰이 확정된 입찰을 {@code WON}으로 전이시킨다.
   *
   * <p>경매가 {@code WON}으로 전이될 때 그 경매의 최고 입찰({@code HIGHEST})도 함께 낙찰로 전이돼야 한다. 여기서 갱신하지 않으면 낙찰
   * 내역 조회({@code bid.bidStatus = WON} 필터)가 영원히 빈 결과를 반환한다.
   *
   * @param bidIds 낙찰로 전이시킬 입찰 식별자 목록
   * @return 실제로 갱신된 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Bid bid
      set bid.bidStatus = com.ootd.pickup.bid.domain.BidStatus.WON
      where bid.bidId in :bidIds
      """)
  int updateBidStatusToWonByIdIn(@Param("bidIds") List<Long> bidIds);
}
