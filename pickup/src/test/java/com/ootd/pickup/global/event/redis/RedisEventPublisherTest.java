package com.ootd.pickup.global.event.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.NotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RedisEventPublisherTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ObjectMapper objectMapper;
  @Mock private NotificationEvent event;
  @Mock private JsonNode payload;

  private RedisEventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    NotificationChannelResolver channelResolver = new NotificationChannelResolver();
    eventPublisher = new RedisEventPublisher(redisTemplate, objectMapper, channelResolver);
  }

  @Test
  void 알림_이벤트를_애그리거트별_Redis_채널에_발행한다() {
    given(event.eventType()).willReturn(EventType.AUCTION_BID_UPDATED);
    given(event.aggregateType()).willReturn(AggregateType.AUCTION);
    given(event.aggregateId()).willReturn(42L);
    given(objectMapper.valueToTree(event)).willReturn(payload);
    given(objectMapper.writeValueAsString(any(NotificationEnvelope.class)))
        .willReturn("serialized-event");

    eventPublisher.publish(event);

    ArgumentCaptor<NotificationEnvelope> envelopeCaptor =
        ArgumentCaptor.forClass(NotificationEnvelope.class);
    then(objectMapper).should().writeValueAsString(envelopeCaptor.capture());
    assertThat(envelopeCaptor.getValue().eventType()).isEqualTo(EventType.AUCTION_BID_UPDATED);
    assertThat(envelopeCaptor.getValue().payload()).isSameAs(payload);
    then(redisTemplate)
        .should()
        .convertAndSend("pickup:notification:AUCTION:42", "serialized-event");
  }

  @Test
  void 이벤트_직렬화에_실패하면_직렬화_예외를_전파한다() {
    given(event.aggregateType()).willReturn(AggregateType.AUCTION);
    given(event.aggregateId()).willReturn(42L);
    JacksonException serializationFailure =
        JacksonException.wrapWithPath(
            new IllegalArgumentException("invalid payload"), event, "payload");
    given(objectMapper.valueToTree(event)).willThrow(serializationFailure);

    assertThatThrownBy(() -> eventPublisher.publish(event)).isSameAs(serializationFailure);
    then(redisTemplate).shouldHaveNoInteractions();
  }
}
