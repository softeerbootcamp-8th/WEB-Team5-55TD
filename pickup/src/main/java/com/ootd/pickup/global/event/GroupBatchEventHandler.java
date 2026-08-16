package com.ootd.pickup.global.event;

import java.util.List;

/**
 * 같은 그룹({@code MessageGroupId}) 이벤트 여러 건을 한 번에 받아 처리할 수 있는 {@link EventHandler}.
 *
 * <p>SQS 소비자가 같은 그룹·같은 이벤트 타입의 연속 구간을 발견하면 {@link #handleBatch}를 호출한다. 그 외의 경우(핸들러가 여러 개이거나 이
 * 인터페이스를 구현하지 않은 경우)는 {@link EventHandler#handle} 경로를 그대로 탄다.
 *
 * @param <E> 이 핸들러가 처리하는 이벤트 타입
 */
public interface GroupBatchEventHandler<E extends MessageQueueEvent> extends EventHandler<E> {

  /**
   * 같은 그룹의 이벤트 여러 건을 순서대로, 가능하면 트랜잭션 하나로 처리한다.
   *
   * <p>반환값은 입력 순서를 지킨 앞부분(prefix)이다 — 처리를 마친(업무상 성공이든 실패든, 둘 다 "끝난" 상태) 이벤트까지만 담고, 예기치 못한 문제를 만나면 그
   * 지점에서 멈춘다. prefix에 없는 이벤트는 이번 호출로 반영되지 않았으므로 호출자가 삭제하면 안 되고, 다음 전달 때 순서대로 다시 시도돼야 한다.
   */
  List<E> handleBatch(List<E> events);

  @Override
  default void handle(E event) {
    List<E> done = handleBatch(List.of(event));
    if (done.isEmpty()) {
      throw new IllegalStateException("배치 처리 중 이벤트가 끝까지 처리되지 못했습니다 - eventId=" + event.eventId());
    }
  }
}
