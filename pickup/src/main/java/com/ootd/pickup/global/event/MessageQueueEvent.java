package com.ootd.pickup.global.event;

/**
 * 메시지 큐 이벤트 — 하나의 소비자만 처리해야 하는 사건.
 *
 * <p>낙찰 정산처럼 후속 처리가 정확히 한 번만 돌아야 하는 이벤트다. 유실이 허용되지 않으므로 {@link EventProducer}로 발행하면 도메인 커밋과 같은
 * 트랜잭션에서 Outbox 테이블에 저장되고, 별도 릴레이가 SQS FIFO 큐로 보낸다.
 *
 * <p>큐는 처리에 실패한 메시지를 다시 전달한다. 핸들러 여러 개 중 하나만 실패해도 메시지 전체가 재전달되므로, 이 계열의 핸들러는 여러 번 실행돼도 결과가 같아야 한다.
 * {@link DomainEvent#eventId()}로 중복을 걸러낸다.
 */
public non-sealed interface MessageQueueEvent extends DomainEvent {}
