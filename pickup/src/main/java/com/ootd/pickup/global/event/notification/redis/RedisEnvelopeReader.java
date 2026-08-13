package com.ootd.pickup.global.event.notification.redis;

import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link RedisEnvelope} 바이트를 열어 알림 이벤트로 되돌린다.
 *
 * <p>{@code eventType}이 {@link EventType}으로 바로 역직렬화되므로, 이 인스턴스가 모르는 상수가 오면 봉투 역직렬화 자체가 {@link
 * JacksonException}으로 실패한다(호출자가 처리) — 지금은 단일 버전만 떠 있어 "모르는 이벤트"와 "메시지가 깨졌다"를 구분할 필요가 없다.
 *
 * <p>알림 계열이 없는 사건(예: 메시지 큐 전용 사건이 실수로 이 채널에 실린 경우)이면 {@code null}을 반환해 건너뛴다. 이벤트 타입은 하드코딩한 맵이 아니라
 * {@link EventType#notificationEventClass()}로 얻는다. 상수를 추가·변경하는 곳은 {@link EventType} 하나로 남기기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEnvelopeReader {

  private final ObjectMapper objectMapper;

  /**
   * @throws JacksonException 봉투 자체가 깨져 역직렬화할 수 없는 경우(모르는 {@code eventType} 상수 포함)
   */
  public NotificationEvent read(byte[] envelopeBytes) {
    RedisEnvelope envelope = objectMapper.readValue(envelopeBytes, RedisEnvelope.class);
    if (envelope == null || envelope.eventType() == null || envelope.payload() == null) {
      log.warn("필수 값이 없는 알림 이벤트를 수신했습니다");
      return null;
    }

    Class<? extends NotificationEvent> eventClass;
    try {
      eventClass = envelope.eventType().notificationEventClass();
    } catch (IllegalStateException exception) {
      log.warn("알림 계열이 없는 사건을 수신했습니다 - eventType={}", envelope.eventType(), exception);
      return null;
    }

    return objectMapper.treeToValue(envelope.payload(), eventClass);
  }
}
