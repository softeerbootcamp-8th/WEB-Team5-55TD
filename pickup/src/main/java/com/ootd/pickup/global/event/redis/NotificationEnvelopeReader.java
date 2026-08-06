package com.ootd.pickup.global.event.redis;

import com.ootd.pickup.global.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEnvelopeReader {

  private final ObjectMapper objectMapper;

  public NotificationEvent read(byte[] envelopeBytes) throws JacksonException {
    NotificationEnvelope envelope =
        objectMapper.readValue(envelopeBytes, NotificationEnvelope.class);
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
