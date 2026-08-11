package com.ootd.pickup.global.event.messagequeue.outbox;

import com.ootd.pickup.global.event.EventProducer;
import com.ootd.pickup.global.event.MessageQueueEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 메시지 큐 이벤트를 Outbox 테이블에 적재하는 {@link EventProducer} 구현체.
 *
 * <p>도메인은 이 클래스도, Outbox 테이블도, SQS도 모른다. {@link EventProducer}만 알고 발행을 맡긴다.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventProducer implements EventProducer {

  /**
   * payload 직렬화에 쓸 매퍼.
   *
   * <p>{@link OutboxEventEntity}는 스프링이 만드는 객체가 아니라 매퍼를 주입받을 수 없다. 엔티티 안에서 새로 만들면 앱이 설정한 것과 갈라지고,
   * 릴레이는 앱 매퍼로 역직렬화하므로 날짜 형식 하나만 달라도 조용히 깨진다. 그래서 주입 지점을 여기 두고 엔티티에 넘긴다.
   */
  private final ObjectMapper objectMapper;

  private final OutboxEventRepository outboxEventJpaRepository;

  /**
   * 이벤트를 Outbox에 적재한다.
   *
   * <p>{@link Propagation#MANDATORY}로 둔 이유는 이 패턴이 "도메인 변경과 이벤트 적재가 같은 커밋에 들어간다"는 것에만 기대고 있기 때문이다. 새
   * 트랜잭션을 열어주면 도메인 변경이 롤백돼도 이벤트만 남아 일어나지 않은 사건이 발행된다. 트랜잭션 없이 부르면 조용히 어긋나는 대신 즉시 실패한다.
   *
   * <p>한 건씩 받아도 배치 적재는 유지된다. {@code persist}는 영속성 컨텍스트에 쌓아두기만 하므로 한 트랜잭션에서 여러 번 불러도 커밋 때 한 문장으로
   * 합쳐진다.
   *
   * @param event 적재할 메시지 큐 이벤트
   * @throws org.springframework.transaction.IllegalTransactionStateException 진행 중인 트랜잭션이 없는 경우
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void produce(MessageQueueEvent event) {
    outboxEventJpaRepository.save(OutboxEventEntity.create(event, objectMapper));
  }
}
