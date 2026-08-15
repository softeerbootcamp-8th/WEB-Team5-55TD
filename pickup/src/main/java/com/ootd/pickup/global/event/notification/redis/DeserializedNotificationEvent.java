package com.ootd.pickup.global.event.notification.redis;

import com.ootd.pickup.global.event.NotificationEvent;

/**
 * {@link RedisEnvelopeReader}가 봉투를 열어 돌려주는 결과.
 *
 * <p>{@code event}만이 아니라 발행 시점의 {@code traceParent}도 함께 돌려줘야, 구독자({@link RedisEventSubscriber})가 그
 * 값으로 원래 발행 트레이스에 스팬을 이어 붙일 수 있다.
 *
 * @param event 되돌린 알림 이벤트
 * @param traceParent 발행 시점의 W3C traceparent. 발행 당시 활성 스팬이 없었으면 {@code null}
 */
public record DeserializedNotificationEvent(NotificationEvent event, String traceParent) {}
