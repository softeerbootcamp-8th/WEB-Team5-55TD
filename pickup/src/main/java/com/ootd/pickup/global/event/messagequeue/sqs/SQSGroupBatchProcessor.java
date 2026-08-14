package com.ootd.pickup.global.event.messagequeue.sqs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

/**
 * 배치를 메시지 그룹별로 나눠 동시에 처리하는 역할.
 *
 * <p>그룹 안에서는 순서대로 처리하고 실패하면 그 뒤 메시지를 건드리지 않지만, 서로 다른 그룹은 {@link #groupExecutor}에서 병렬로 처리한다. 한 그룹의
 * 처리가 오래 걸리거나 실패해도 다른 그룹은 기다리지 않는다.
 *
 * <p>큐가 그룹당 한 번에 하나만 준다고 기대할 수 없다. FIFO 큐는 같은 그룹의 메시지를 한 응답에 순서대로 함께 실어 준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "event.sqs.enabled", havingValue = "true")
public class SQSGroupBatchProcessor {

  /** Datadog 등에서 에러를 Critical로 수집하기 위한 마커. */
  private static final Marker CRITICAL_MARKER = MarkerFactory.getMarker("CRITICAL");

  private final SQSMessageDispatcher dispatcher;

  /** 배치 안에서 서로 다른 메시지 그룹을 동시에 처리하는 데 쓴다. 그룹 안 순서는 이 실행기와 무관하게 호출 순서로 지킨다. */
  private final ExecutorService groupExecutor;

  /**
   * 배치를 메시지 그룹별로 나눠 동시에 처리하고, 삭제할 메시지를 그룹 순서대로 모아 돌려준다.
   *
   * <p>모든 그룹 작업이 끝나야 반환한다 — 호출자 입장에서는 동기 호출이다.
   */
  List<Message> process(List<Message> messages) {
    List<Future<List<Message>>> groupFutures =
        groupByMessageGroupId(messages).values().stream()
            .map(groupMessages -> groupExecutor.submit(() -> processGroup(groupMessages)))
            .toList();

    List<Message> consumed = new ArrayList<>();
    for (Future<List<Message>> groupFuture : groupFutures) {
      consumed.addAll(awaitGroup(groupFuture));
    }
    return consumed;
  }

  /** 배치 안 메시지를 그룹 등장 순서대로 묶는다. 그룹 안 메시지 순서는 원래 순서 그대로 보존한다. */
  private Map<String, List<Message>> groupByMessageGroupId(List<Message> messages) {
    Map<String, List<Message>> messagesByGroup = new LinkedHashMap<>();
    for (Message message : messages) {
      String messageGroupId = message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
      messagesByGroup.computeIfAbsent(messageGroupId, key -> new ArrayList<>()).add(message);
    }
    return messagesByGroup;
  }

  /**
   * 한 그룹의 메시지를 순서대로 처리하고 성공한 메시지만 모은다.
   *
   * <p>처리에 실패하면 그 뒤 메시지는 이번 배치에서 더 처리하지 않는다. 계속 처리하면 앞 이벤트가 재전달돼 다시 처리될 때 이미 뒤 이벤트가 반영돼 있어 같은 그룹
   * 안에서 순서가 역전된다. 건너뛴 메시지도 삭제하지 않으므로 다음 전달 때 순서대로 다시 처리된다.
   *
   * <p>{@link #groupExecutor}의 작업 스레드에서 실행된다.
   */
  private List<Message> processGroup(List<Message> messages) {
    List<Message> consumed = new ArrayList<>();
    for (Message message : messages) {
      try {
        dispatcher.dispatch(message);
        consumed.add(message);
      } catch (RuntimeException exception) {
        log.error(
            CRITICAL_MARKER,
            "SQS 이벤트 처리에 실패했습니다 - messageId={}, messageGroupId={}",
            message.messageId(),
            message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID),
            exception);
        break;
      }
    }
    return consumed;
  }

  /**
   * 그룹 작업이 끝나기를 기다린다.
   *
   * <p>인터럽트되면 이번 그룹의 결과를 포기하고 인터럽트 상태를 되살린다. 아직 도는 다른 그룹의 대기도 곧 같은 인터럽트를 받으므로 배치 전체가 빠르게 정리된다.
   *
   * @throws IllegalStateException {@link #processGroup}이 아닌 다른 경로로 작업 자체가 실패한 경우. 프로그래밍 오류다
   */
  private List<Message> awaitGroup(Future<List<Message>> groupFuture) {
    try {
      return groupFuture.get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return List.of();
    } catch (ExecutionException exception) {
      throw new IllegalStateException("SQS 메시지 그룹 처리 작업이 실패했습니다", exception.getCause());
    }
  }
}
