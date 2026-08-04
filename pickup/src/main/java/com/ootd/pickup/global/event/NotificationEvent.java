package com.ootd.pickup.global.event;

/**
 * 알림 이벤트 — 구독한 모든 소비자가 각자 처리하는 사건.
 *
 * <p>입찰로 인한 현재가 변동처럼, SSE 연결을 가진 모든 인스턴스가 받아 각자 자기 연결에 흘려보내야 하는 이벤트다. 한 인스턴스만 받으면 다른 인스턴스에 붙은 사용자는
 * 현재가를 못 본다.
 *
 * <p>유실이 허용된다. {@link EventPublisher}로 발행하면 Outbox를 거치지 않고 Redis Pub/Sub 채널로 즉시 나간다. 발행이 가벼운 대신 그
 * 순간 구독자가 없으면 그대로 사라진다.
 *
 * <p>한 번 뿌리고 끝이라 같은 이벤트가 두 번 전달되지는 않는다. 이 계열의 핸들러는 중복을 걸러낼 필요가 없다.
 */
public non-sealed interface NotificationEvent extends DomainEvent {}
