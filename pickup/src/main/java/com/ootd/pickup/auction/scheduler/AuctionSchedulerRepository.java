package com.ootd.pickup.auction.scheduler;

import static com.ootd.pickup.auction.domain.QAuction.auction;
import static com.ootd.pickup.bid.domain.QBid.bid;
import static com.ootd.pickup.consignments.domain.QConsignment.consignment;
import static com.ootd.pickup.member.domain.QMember.member;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/**
 * 경매 스케줄러 전용 영속성. MVC 계층의 {@code AuctionRepository}와 {@link Auction} 엔티티만 공유한다.
 *
 * <p>상태 전이는 반드시 QueryDSL bulk update로만 일어난다. 엔티티를 읽어 수정하면 JPA 변경 감지가 행 전체를 쓰기 때문에, 마감 직전 입찰이 갱신한
 * {@code winning_price}를 덮어 유실시킨다. bulk update는 그 컬럼을 건드리지 않으므로 입찰과 경쟁하지 않고 비관적 락도 필요 없다.
 *
 * <p>전이 메서드는 도착 상태를 파라미터로 받지 않고 이름에 박아 둔다. 낙찰과 유찰은 조건이 서로 다르므로, 상태를 넘길 수 있게 하면 조건과 도착 상태가 어긋난 조합을
 * 호출할 수 있다.
 */
@Repository
@RequiredArgsConstructor
public class AuctionSchedulerRepository {

  private final AuctionSchedulerJpaRepository auctionSchedulerJpaRepository;
  private final JPAQueryFactory queryFactory;
  private final EntityManager entityManager;

  /**
   * 시작 시각에 도달한 경매의 식별자를 조회한다.
   *
   * <p>엔티티가 아니라 식별자만 읽는다. 엔티티를 영속성 컨텍스트에 올리면 뒤이은 bulk update와 상태가 어긋난다.
   *
   * <p>기준 시각은 {@code LocalDateTime.now()}가 아닌 DB 서버 시각을 쓴다. 인스턴스마다 시계가 어긋나면 어느 인스턴스가 이 작업을 잡았는지에 따라
   * 전이 대상이 갈라진다. {@link
   * AuctionSchedulerJpaRepository#findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow}가 {@code
   * local datetime}으로 DB 시각을 사용하며, QueryDSL 위임으로 동일한 보장을 제공한다.
   *
   * <p><b>정렬 키를 식별자로 바꾸지 않는다.</b> 밀린 물량이 {@code limit}을 넘을 때 시작 시각이 이른 경매부터 처리해야 한다.
   *
   * @param auctionStatus 전이 전 상태
   * @param limit 한 번에 처리할 최대 건수
   * @return 시작 시각이 이른 경매부터 정렬된 식별자 목록. 대상이 없으면 빈 목록
   */
  public List<Long> findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow(
      AuctionStatus auctionStatus, Limit limit) {
    return auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndStartedAtLessThanEqualNow(
        auctionStatus, limit);
  }

  /**
   * 종료 시각에 도달한 경매의 식별자를 조회한다.
   *
   * <p>{@code endedAt}이 null인 경매는 비교식이 참이 되지 않아 자연히 제외된다.
   *
   * @param auctionStatus 전이 전 상태
   * @param limit 한 번에 처리할 최대 건수
   * @return 종료 시각이 이른 경매부터, 즉 가장 오래 밀린 경매부터 정렬된 식별자 목록. 대상이 없으면 빈 목록
   */
  public List<Long> findAllIdsByAuctionStatusAndEndedAtLessThanEqualNow(
      AuctionStatus auctionStatus, Limit limit) {
    return auctionSchedulerJpaRepository.findAllIdsByAuctionStatusAndEndedAtLessThanEqualNow(
        auctionStatus, limit);
  }

  /**
   * 예정 상태인 경매를 진행 중으로 전이시킨다.
   *
   * <p>{@code SCHEDULED} 조건은 조회와 갱신 사이에 상태가 바뀐 경매를 제외한다.
   *
   * <p>QueryDSL bulk update는 영속성 컨텍스트를 거치지 않고 DB를 직접 갱신한다. 이 메서드 호출 이전에 같은 트랜잭션에서 이미 로드된 {@link
   * Auction}이 있으면 1차 캐시가 갱신 전 상태를 그대로 들고 있어, 이어지는 조회가 stale한 값을 돌려준다. 그래서 갱신 직후 영속성 컨텍스트를 비운다.
   *
   * @param auctionIds 전이 대상 식별자 목록
   * @return 실제로 갱신된 건수
   */
  public int updateAuctionStatusToOngoingByIdIn(List<Long> auctionIds) {
    int updated =
        (int)
            queryFactory
                .update(auction)
                .set(auction.auctionStatus, AuctionStatus.ONGOING)
                .where(
                    auction.auctionId.in(auctionIds),
                    auction.auctionStatus.eq(AuctionStatus.SCHEDULED))
                .execute();
    entityManager.clear();
    return updated;
  }

  /**
   * 리저브를 채운 경매를 낙찰로 전이시킨다.
   *
   * <p>리저브 비교를 DB에서 한다. 엔티티를 로드해 비교하면 {@code winning_price}를 읽은 시점과 갱신 시점이 벌어져, 그 사이에 들어온 마감 직전 입찰이
   * 판정에 반영되지 않는다.
   *
   * <p>갱신 직후 영속성 컨텍스트를 비운다. {@link #updateAuctionStatusToOngoingByIdIn}와 같은 이유다.
   *
   * @param auctionIds 전이 대상 식별자 목록
   * @return 낙찰로 갱신된 건수
   */
  public int updateAuctionStatusToWonByIdIn(List<Long> auctionIds) {
    int updated =
        (int)
            queryFactory
                .update(auction)
                .set(auction.auctionStatus, AuctionStatus.WON)
                .where(
                    auction.auctionId.in(auctionIds),
                    auction.auctionStatus.eq(AuctionStatus.ONGOING),
                    auction.winningPrice.isNotNull(),
                    auction.winningPrice.goe(auction.reservePrice))
                .execute();
    entityManager.clear();
    return updated;
  }

  /**
   * 리저브를 채우지 못한 경매를 유찰로 전이시킨다.
   *
   * <p>입찰이 없어 {@code winning_price}가 null인 경우도 유찰이다.
   *
   * <p>갱신 직후 영속성 컨텍스트를 비운다. {@link #updateAuctionStatusToOngoingByIdIn}와 같은 이유다.
   *
   * @param auctionIds 전이 대상 식별자 목록
   * @return 유찰로 갱신된 건수
   */
  public int updateAuctionStatusToPassedByIdIn(List<Long> auctionIds) {
    int updated =
        (int)
            queryFactory
                .update(auction)
                .set(auction.auctionStatus, AuctionStatus.PASSED)
                .where(
                    auction.auctionId.in(auctionIds),
                    auction.auctionStatus.eq(AuctionStatus.ONGOING),
                    auction.winningPrice.isNull().or(auction.winningPrice.lt(auction.reservePrice)))
                .execute();
    entityManager.clear();
    return updated;
  }

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
  public List<Auction> findAllWithConsignmentAndSellerMemberByIdIn(List<Long> auctionIds) {
    return queryFactory
        .selectFrom(auction)
        .join(auction.consignment, consignment)
        .fetchJoin()
        .join(consignment.sellerMember, member)
        .fetchJoin()
        .where(auction.auctionId.in(auctionIds))
        .fetch();
  }

  /**
   * 입찰을 입찰자와 함께 조회한다.
   *
   * <p>낙찰 이벤트에 낙찰자 식별자를 담기 위한 것이다. 경매의 {@code winningBidId}로 찾으며, 유찰된 경매는 대상이 없다.
   *
   * @param bidIds 조회할 입찰 식별자 목록
   * @return 입찰자가 함께 로드된 입찰 목록
   */
  public List<Bid> findAllBidsWithMemberByIdIn(List<Long> bidIds) {
    return queryFactory
        .selectFrom(bid)
        .join(bid.member, member)
        .fetchJoin()
        .where(bid.bidId.in(bidIds))
        .fetch();
  }
}
