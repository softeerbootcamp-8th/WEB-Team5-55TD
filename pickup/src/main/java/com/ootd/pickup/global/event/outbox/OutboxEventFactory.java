package com.ootd.pickup.global.event.outbox;

import com.ootd.pickup.global.event.MessageQueueEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 메시지 큐 이벤트를 Outbox 행으로 변환한다.
 *
 * <p>변환에 필요한 {@link ObjectMapper}를 주입받기 위해 존재하는 빈이다. {@link OutboxEventEntity}는 스프링이 만드는 객체가 아니라 —
 * 우리 코드의 {@code new}와 Hibernate의 리플렉션으로 생성된다 — 매퍼를 주입받을 수 없다. 그 주입 지점을 여기로 옮겨 호출자가 매퍼를 들고 다니지 않게
 * 한다.
 *
 * <p>payload는 직렬화와 역직렬화가 같은 매퍼 설정을 써야 하므로, 릴레이도 같은 빈에서 나온 설정으로 되돌려야 한다.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

  private final ObjectMapper objectMapper;

  /**
   * 이벤트 하나를 적재 대상 행으로 변환한다.
   *
   * @param event 적재할 메시지 큐 이벤트
   * @return 아직 발행되지 않은 상태의 Outbox 행
   */
  public OutboxEventEntity create(MessageQueueEvent event) {
    return OutboxEventEntity.create(event, objectMapper);
  }

  /**
   * 여러 이벤트를 적재 대상 행으로 변환한다. 배치 적재의 입력이다.
   *
   * @param events 적재할 메시지 큐 이벤트 목록
   * @return 입력과 같은 순서의 Outbox 행 목록
   */
  public List<OutboxEventEntity> createAll(List<? extends MessageQueueEvent> events) {
    return events.stream().map(this::create).toList();
  }
}
