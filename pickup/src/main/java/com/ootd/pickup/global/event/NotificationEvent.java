package com.ootd.pickup.global.event;

/**
 * 알림 이벤트 — 구독한 모든 소비자가 각자 처리하는 사건.
 *
 * <p>입찰로 인한 현재가 변동처럼, WebSocket 연결을 가진 모든 인스턴스가 받아 각자 자기 연결에 흘려보내야 하는 이벤트다. 한 인스턴스만 받으면 다른 인스턴스에 붙은
 * 사용자는 현재가를 못 본다.
 *
 * <p>유실이 허용된다. {@link EventPublisher}로 발행하면 Outbox를 거치지 않고 Redis Pub/Sub 채널로 즉시 나간다. 발행이 가벼운 대신 그
 * 순간 구독자가 없으면 그대로 사라진다.
 *
 * <p>실패한 이벤트를 재전달하지 않는다. 처리 실패는 핸들러별로 격리해 다른 핸들러가 계속 실행되게 한다.
 */
public non-sealed interface NotificationEvent extends DomainEvent {}
