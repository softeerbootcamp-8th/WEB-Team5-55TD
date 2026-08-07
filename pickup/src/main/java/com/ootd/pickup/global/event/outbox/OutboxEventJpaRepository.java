package com.ootd.pickup.global.event.outbox;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Outbox 행 영속화.
 *
 * <p>{@link org.springframework.data.repository.CrudRepository#saveAll}이 배치 적재 경로다. {@link
 * OutboxEventEntity}가 {@link org.springframework.data.domain.Persistable}을 구현해 {@code persist}로 가고,
 * {@code hibernate.jdbc.batch_size} 설정에 의해 JDBC 배치로 묶인다.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, String> {

  /**
   * 아직 발행되지 않은 행을 오래된 순으로 조회한다.
   *
   * <p>{@code created_at} 오름차순인 이유는 같은 애그리거트의 사건 순서를 지켜야 하기 때문이다. 경매 시작과 종료가 뒤바뀐 순서로 큐에 들어가면 소비자가
   * 잘못된 순서로 처리한다. {@code (published, created_at)} 인덱스가 이 조회를 받는다.
   *
   * @param limit 한 번에 발행할 최대 건수
   * @return 발행 대기 중인 행 목록. 없으면 빈 목록
   */
  List<OutboxEventEntity> findAllByPublishedFalseOrderByCreatedAtAsc(Limit limit);

  /**
   * 전송에 성공한 행을 발행 완료로 표시한다.
   *
   * <p>{@link OutboxEventEntity}에 값을 바꾸는 메서드를 두지 않고 bulk update로 처리한다. 인스턴스 메서드를 만들면 {@code
   * isNew()}가 항상 {@code true}라는 전제가 깨진다.
   *
   * @param ids 발행에 성공한 이벤트 식별자 목록
   * @return 표시된 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update OutboxEventEntity outboxEvent
      set outboxEvent.published = true
      where outboxEvent.id in :ids
      """)
  int updatePublishedByIdIn(@Param("ids") List<String> ids);
}
