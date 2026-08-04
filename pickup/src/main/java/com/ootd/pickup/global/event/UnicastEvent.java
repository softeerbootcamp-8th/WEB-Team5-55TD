package com.ootd.pickup.global.event;

/**
 * 단일 처리 이벤트 — 하나의 소비자만 처리해야 하는 사건.
 *
 * <p>경매 시작·종료처럼 후속 처리가 정확히 한 번만 돌아야 하는 이벤트다. 낙찰 처리가 두 번 돌면 안 되므로 여러 인스턴스가 붙어도 하나만 받아야 한다.
 *
 * <p>유실이 허용되지 않는다. {@link EventProducer}로 발행하면 Outbox 테이블에 먼저 저장되고, 별도 릴레이가 SQS FIFO 큐로 보낸다. DB 커밋과
 * 같은 트랜잭션에서 저장되므로 커밋 직후 프로세스가 죽어도 이벤트가 사라지지 않는다.
 *
 * <p>SQS는 처리에 실패한 메시지를 다시 전달한다. 핸들러 여러 개 중 하나만 실패해도 메시지 전체가 재전달되므로, 이 계열의 핸들러는 여러 번 실행돼도 결과가 같아야
 * 한다. {@link DomainEvent#eventId()}로 중복을 걸러낸다.
 */
public non-sealed interface UnicastEvent extends DomainEvent {}
