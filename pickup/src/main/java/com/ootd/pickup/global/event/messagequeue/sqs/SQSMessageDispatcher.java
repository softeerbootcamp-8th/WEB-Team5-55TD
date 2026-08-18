package com.ootd.pickup.global.event.messagequeue.sqs;

import com.ootd.pickup.global.event.DomainEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import tools.jackson.databind.ObjectMapper;

/**
 * SQS 메시지 하나를 이벤트로 되돌려 타입이 맞는 핸들러 전부에게 넘기는 역할.
 *
 * <p>메시지 형식은 {@link SQSMessageQueueSender}가 정한다. 본문은 적재 시점의 JSON 원문이고, 되돌릴 타입은 {@value
 * #EVENT_TYPE_ATTRIBUTE} 속성으로 온다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "event.sqs.enabled", havingValue = "true")
public class SQSMessageDispatcher {

  /** {@link SQSMessageQueueSender}가 싣는 속성 이름. 양쪽이 같아야 한다. */
  static final String EVENT_TYPE_ATTRIBUTE = "eventType";

  /** 알 수 없는 값 자체를 태그로 쓰면 지표 카디널리티가 늘어나므로 하나의 유한 값으로 합친다. */
  private static final String UNKNOWN_EVENT_TYPE = "UNKNOWN";

  /** 적재 시점과 같은 매퍼여야 한다. 날짜 형식 하나만 달라도 왕복이 깨진다. */
  private final ObjectMapper objectMapper;

  private final Map<Class<? extends DomainEvent>, List<EventHandler<DomainEvent>>>
      handlersByEventClass;

  /**
   * 등록된 핸들러를 처리 대상 타입으로 묶어 둔다.
   *
   * <p>{@link EventHandler#eventClass()}가 선언한 타입이 곧 분배 기준이라, 소비 시점에 {@code instanceof} 분기가 필요 없다.
   *
   * @param eventHandlers 스프링이 찾은 모든 핸들러. 알림 계열 핸들러도 함께 들어오지만 큐로 오는 이벤트와 타입이 겹치지 않아 호출되지 않는다
   */
  @SuppressWarnings("unchecked")
  public SQSMessageDispatcher(
      ObjectMapper objectMapper, List<EventHandler<? extends DomainEvent>> eventHandlers) {
    this.objectMapper = objectMapper;
    this.handlersByEventClass =
        eventHandlers.stream()
            .collect(
                Collectors.groupingBy(
                    EventHandler::eventClass,
                    Collectors.mapping(
                        handler -> (EventHandler<DomainEvent>) handler, Collectors.toList())));
  }

  /**
   * 메시지 하나를 이벤트로 되돌려 타입이 맞는 핸들러 전부에게 넘긴다.
   *
   * <p>핸들러가 던진 예외를 잡지 않는다. 삼키면 메시지가 삭제되어 처리되지 않은 이벤트가 사라진다. 핸들러 여러 개 중 하나만 실패해도 메시지 전체가 재전달되므로 이미
   * 성공한 핸들러도 다시 실행된다. 그래서 이 계열의 핸들러는 여러 번 실행돼도 결과가 같아야 한다({@link EventHandler} 참고).
   *
   * @throws IllegalStateException 되돌린 타입을 처리할 핸들러가 없는 경우
   */
  void dispatch(Message message) {
    MessageQueueEvent event = toEvent(message);
    List<EventHandler<DomainEvent>> handlers =
        handlersByEventClass.getOrDefault(event.getClass(), List.of());

    if (handlers.isEmpty()) {
      throw new IllegalStateException(
          "이벤트를 처리할 핸들러가 없습니다 - eventType=" + event.eventType() + ", eventId=" + event.eventId());
    }
    for (EventHandler<DomainEvent> handler : handlers) {
      handler.handle(event);
    }
    log.debug(
        "SQS 이벤트 처리를 완료했습니다 - eventId={}, eventType={}, messageId={}",
        event.eventId(),
        event.eventType(),
        message.messageId());
  }

  /**
   * 본문을 {@code eventType} 속성이 가리키는 타입으로 되돌린다.
   *
   * @throws IllegalStateException 속성이 없는 경우
   * @throws IllegalArgumentException 아는 {@link EventType}이 아닌 경우
   */
  private MessageQueueEvent toEvent(Message message) {
    MessageAttributeValue attribute = message.messageAttributes().get(EVENT_TYPE_ATTRIBUTE);
    if (attribute == null || attribute.stringValue() == null) {
      throw new IllegalStateException(
          "메시지에 " + EVENT_TYPE_ATTRIBUTE + " 속성이 없습니다 - messageId=" + message.messageId());
    }
    EventType eventType = EventType.valueOf(attribute.stringValue());
    return objectMapper.readValue(message.body(), eventType.messageQueueEventClass());
  }

  /** 지표 태그로 쓸 이벤트 타입을 구한다. 속성이 없거나 아는 {@link EventType}이 아니면 {@value #UNKNOWN_EVENT_TYPE}을 돌려준다. */
  static String eventTypeTag(Message message) {
    MessageAttributeValue attribute = message.messageAttributes().get(EVENT_TYPE_ATTRIBUTE);
    if (attribute == null || attribute.stringValue() == null) {
      return UNKNOWN_EVENT_TYPE;
    }
    try {
      return EventType.valueOf(attribute.stringValue()).name();
    } catch (IllegalArgumentException exception) {
      return UNKNOWN_EVENT_TYPE;
    }
  }
}
