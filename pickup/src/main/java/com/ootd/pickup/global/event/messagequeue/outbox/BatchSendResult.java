package com.ootd.pickup.global.event.messagequeue.outbox;

import java.util.List;

/**
 * {@link MessageQueueSender#sendBatch}의 결과.
 *
 * <p>배치 전송은 부분 성공이 정상 응답이다 — SQS {@code SendMessageBatch}가 항목별 성공/실패를 응답 안에 함께 돌려주므로, 이걸 예외 하나로
 * 뭉뚱그리면 부분 성공 정보가 사라진다. 그래서 예외가 아니라 반환값으로 표현한다. 호출 자체가 실패한 경우(네트워크 등)는 여전히 예외로 던진다 — 그때는 호출자가 청크
 * 전체를 실패로 취급한다.
 *
 * @param succeededEventIds 큐에 실제로 들어간 이벤트의 {@code eventId} 목록
 * @param failedEvents 들어가지 않은 이벤트와 그 사유
 */
public record BatchSendResult(List<String> succeededEventIds, List<FailedEvent> failedEvents) {

  /**
   * @param eventId 실패한 이벤트의 식별자
   * @param reason 사람이 읽을 수 있는 실패 사유(예: SQS 에러 코드)
   */
  public record FailedEvent(String eventId, String reason) {}
}
