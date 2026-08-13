package com.ootd.pickup.global.event.messagequeue.outbox;

import static com.ootd.pickup.global.event.messagequeue.outbox.QOutboxEventEntity.outboxEventEntity;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 행 영속화를 위한 QueryDSL 기반 리포지토리.
 *
 * <p>{@link OutboxEventJpaRepository}의 Spring Data 파생 메서드는 그대로 위임하고, 이 클래스는 QueryDSL이 필요한 bulk
 * update 쿼리를 제공한다.
 */
@Repository
@RequiredArgsConstructor
public class OutboxEventRepository {

  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final JPAQueryFactory queryFactory;
  private final EntityManager entityManager;

  /**
   * 아직 발행되지 않은 행을 오래된 순으로 조회한다.
   *
   * @param limit 한 번에 발행할 최대 건수
   * @return 발행 대기 중인 행 목록. 없으면 빈 목록
   */
  public List<OutboxEventEntity> findAllByPublishedFalseOrderByCreatedAtAsc(Limit limit) {
    return outboxEventJpaRepository.findAllByPublishedFalseOrderByCreatedAtAsc(limit);
  }

  /**
   * 이벤트를 Outbox에 적재한다.
   *
   * @param entity 적재할 엔티티
   */
  public void save(OutboxEventEntity entity) {
    outboxEventJpaRepository.save(entity);
  }

  public void deleteAll() {
    outboxEventJpaRepository.deleteAll();
  }

  public java.util.Optional<OutboxEventEntity> findById(String id) {
    return outboxEventJpaRepository.findById(id);
  }

  /**
   * 전송에 성공한 행을 발행 완료로 표시한다.
   *
   * <p>{@link OutboxEventEntity}에 값을 바꾸는 메서드를 두지 않고 QueryDSL bulk update로 처리한다. 인스턴스 메서드를 만들면
   * {@code isNew()}가 항상 {@code true}라는 전제가 깨진다.
   *
   * <p>QueryDSL bulk update는 영속성 컨텍스트를 거치지 않고 DB를 직접 갱신하므로, 갱신 직후 영속성 컨텍스트를 비워 이미 로드된 엔티티가 갱신 전 상태로
   * 남는 것을 막는다.
   *
   * @param ids 발행에 성공한 이벤트 식별자 목록
   * @return 표시된 건수
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public int updatePublishedByIdIn(List<String> ids) {
    int updated =
        (int)
            queryFactory
                .update(outboxEventEntity)
                .set(outboxEventEntity.published, true)
                .where(outboxEventEntity.id.in(ids))
                .execute();
    entityManager.clear();
    return updated;
  }

  /**
   * 발행 완료 후 보존 기간이 지난 행을 지운다.
   *
   * <p>{@code published=false}인 행은 조건에서 제외한다. 발행에 계속 실패해 남은 행(poison message)까지 지우면 아직 큐에 들어가지 않은
   * 이벤트가 유실된다. {@link OutboxEventCleanupScheduler}만 이 메서드를 호출한다.
   *
   * @param threshold 이 시각보다 이전에 발생한(occurredAt 기준) 이벤트만 지운다
   * @return 지워진 건수
   */
  @Transactional
  public int deleteByPublishedTrueAndCreatedAtBefore(LocalDateTime threshold) {
    int deleted =
        (int)
            queryFactory
                .delete(outboxEventEntity)
                .where(
                    outboxEventEntity.published.isTrue(),
                    outboxEventEntity.createdAt.before(threshold))
                .execute();
    entityManager.clear();
    return deleted;
  }
}
