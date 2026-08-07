package com.ootd.pickup.global.event.outbox;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.DomainEvent;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;
import tools.jackson.databind.ObjectMapper;

/**
 * 발행을 기다리는 {@link MessageQueueEvent}의 영속 형태.
 *
 * <p>도메인 트랜잭션과 같은 커밋에 적재되어, 커밋 직후 프로세스가 죽어도 이벤트가 사라지지 않게 한다. {@link OutboxEventScheduler}가 {@code
 * published=false}인 행을 읽어 큐로 발행한다.
 *
 * <p>컬럼은 {@link DomainEvent}의 메서드에 1:1로 대응한다. {@link #published}만 예외로, 사건의 성질이 아니라 릴레이의 전송 상태다.
 */
@Entity
@Table(
    name = "outbox_event",
    indexes =
        @Index(
            name = "idx_outbox_event_published_created_at",
            columnList = "published, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity implements Persistable<String> {

  /** {@link MessageQueueEvent#eventId()}를 그대로 PK로 쓴다. 같은 이벤트가 두 번 적재되면 PK 충돌로 드러난다. */
  @Id
  @Column(name = "id", length = 36, nullable = false)
  private String id;

  @Enumerated(EnumType.STRING)
  @Column(name = "aggregate_type", length = 50, nullable = false)
  private AggregateType aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  /** 이름을 그대로 저장한다. 수신 측이 이 값으로 되돌릴 타입을 정한다. */
  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", length = 50, nullable = false)
  private EventType eventType;

  /**
   * 이벤트 record 전체를 직렬화한 JSON.
   *
   * <p>{@code columnDefinition = "json"}이 아니라 {@link SqlTypes#JSON}을 쓰는 이유는 dialect별 처리를 Hibernate에
   * 맡기기 위해서다. 문자열을 그대로 바인딩하면 H2가 JSON 문자열 스칼라로 이중 인코딩해 왕복이 깨진다.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false)
  private String payload;

  /** 릴레이의 전송 완료 여부. 소비자의 처리 완료 여부가 아니다. */
  @Column(name = "published", nullable = false)
  private boolean published;

  /** 사건이 발생한 시각. 적재 시각이 아니라 {@link MessageQueueEvent#occurredAt()}을 그대로 쓴다. */
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  private OutboxEventEntity(MessageQueueEvent event, String payload) {
    this.id = event.eventId();
    this.aggregateType = event.aggregateType();
    this.aggregateId = event.aggregateId();
    this.eventType = event.eventType();
    this.payload = payload;
    this.published = false;
    this.createdAt = event.occurredAt();
  }

  /**
   * 이벤트를 적재 대상 행으로 만든다.
   *
   * <p>직렬화 결과가 아니라 {@link ObjectMapper}를 받는 이유는 {@code event}와 payload가 서로 맞는지 보장하기 위해서다. 문자열을 받으면
   * 다른 이벤트의 payload를 넘겨도 컴파일된다.
   *
   * <p>package-private이라 매퍼를 고르는 곳은 {@link OutboxEventProducer} 하나다. payload는 릴레이가 같은 설정으로 역직렬화해야
   * 하므로 호출자마다 다른 매퍼를 넘길 수 있으면 조용히 어긋난다.
   *
   * @param event 적재할 메시지 큐 이벤트
   * @param objectMapper payload 직렬화에 쓸 매퍼
   * @return 아직 발행되지 않은 상태의 Outbox 행
   */
  static OutboxEventEntity create(MessageQueueEvent event, ObjectMapper objectMapper) {
    return new OutboxEventEntity(event, objectMapper.writeValueAsString(event));
  }

  /**
   * 이 행을 큐로 보낼 수 있는 형태로 바꾼다.
   *
   * <p>이 클래스가 {@link MessageQueueEvent}를 직접 구현하지 않는 이유는 사건과 사건의 기록이 다른 것이기 때문이다. 이 행에는 {@link
   * #published}처럼 사건의 성질이 아닌 값이 있고 bulk update로 바뀐다. 반면 이벤트는 일어난 사실이라 불변이다.
   *
   * @return payload 원문을 그대로 실은 전송 대상
   */
  public MessageQueueEvent toEvent() {
    return RelayedOutboxEvent.from(this);
  }

  /**
   * PK를 애플리케이션이 부여해 저장 전에도 non-null이라, 이 값을 알려주지 않으면 Spring Data가 기존 행으로 보아 {@code merge}로 처리한다.
   * 그러면 INSERT 전에 행마다 존재 확인 SELECT가 붙어 배치 적재의 이점이 사라진다.
   */
  @Override
  public boolean isNew() {
    return true;
  }
}
