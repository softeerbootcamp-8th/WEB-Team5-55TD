package com.ootd.pickup.global.event.redis;

import com.ootd.pickup.global.event.EventType;
import tools.jackson.databind.JsonNode;

/**
 * Redis Pub/Sub 채널에 실제로 실리는 메시지 포장.
 *
 * <p>{@link com.ootd.pickup.global.event.NotificationEvent} record는 {@code eventType()}을 record
 * 컴포넌트가 아니라 오버라이드 메서드로 둬 직렬화 대상에서 빠진다({@code DomainEvent} 계약). 채널로 보낼 땐 이 값을 별도로 실어야 구독자가 어떤 타입으로
 * 되돌릴지 알 수 있다 — {@code SQSMessageQueueSender}가 본문에 없는 {@code eventType}을 메시지 속성에 따로 싣는 것과 같은 이유다.
 * 거긴 메시지 속성, 여긴 봉투라는 차이만 있다.
 *
 * <p>{@code eventType}을 문자열이 아니라 {@link EventType}으로 바로 받는다. 여러 버전의 인스턴스가 동시에 떠 있는 롤링 디플로이 상황에서는
 * 구버전이 아직 모르는 상수가 오면 이 필드가 문자열이어야 봉투 자체는 역직렬화에 성공한다 — 하지만 지금은 단일 버전만 존재해 그 구분이 아무 의미가 없다. 실제로 그 상황이
 * 생기면(운영 배포가 시작되면) 그때 다시 문자열로 낮춘다.
 */
public record NotificationEnvelope(EventType eventType, JsonNode payload) {}
