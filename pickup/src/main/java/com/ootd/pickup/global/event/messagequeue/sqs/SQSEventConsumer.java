package com.ootd.pickup.global.event.messagequeue.sqs;

import com.ootd.pickup.global.event.DomainEvent;
import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.MessageQueueEvent;
import com.ootd.pickup.global.event.messagequeue.sqs.config.SQSProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * SQS FIFO 큐에서 메시지를 받아 이벤트 핸들러로 넘기는 소비자.
 *
 * <p>메시지 형식은 {@link SQSMessageQueueSender}가 정한다. 본문은 적재 시점의 JSON 원문이고, 되돌릴 타입은 {@code eventType}
 * 속성으로 온다.
 *
 * <p>전용 스레드 하나에서 롱 폴링한다. <b>{@code @Scheduled}로 옮기면 안 된다.</b> {@code ReceiveMessage}가 대기 시간만큼 스레드를
 * 붙잡고 있어 스케줄러 풀 슬롯을 상시 점유하고, 같은 풀을 쓰는 경매·Outbox 작업의 주기가 함께 밀린다.
 *
 * <p><b>ShedLock도 걸면 안 된다.</b> "한 소비자만 처리"는 큐가 보장한다. 잠금을 걸면 인스턴스를 늘려도 소비량이 늘지 않는다.
 *
 * <p>처리에 실패한 메시지는 <b>삭제하지 않는다.</b> 가시성 제한 시간이 지나면 다시 전달되고, 재시도가 반복되면 큐의 재구동 정책에 따라 DLQ로 간다. 되돌릴 수
 * 없는 메시지(알 수 없는 {@code eventType}, 역직렬화 실패, 핸들러 없음)도 같은 경로를 탄다. 조용히 버리면 유실이 허용되지 않는 이벤트가 사라지므로,
 * DLQ에 남겨 사람이 보게 한다.
 *
 * <p><b>큐에 DLQ 재구동 정책이 없으면 실패한 메시지가 영원히 재전달되고, FIFO 특성상 같은 경매의 뒤 이벤트가 전부 그 뒤에 막힌다.</b>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "event.sqs.enabled", havingValue = "true")
public class SQSEventConsumer implements SmartLifecycle {

  /** {@link SQSMessageQueueSender}가 싣는 속성 이름. 양쪽이 같아야 한다. */
  private static final String EVENT_TYPE_ATTRIBUTE = "eventType";

  /** Datadog 등에서 에러를 Critical로 수집하기 위한 마커. */
  private static final Marker CRITICAL_MARKER = MarkerFactory.getMarker("CRITICAL");

  /** 큐 접속 자체가 실패할 때 폴링이 쉬지 않고 도는 것을 막는 간격. */
  private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);

  /**
   * 종료 시 처리 중인 배치가 끝나기를 기다리는 시간.
   *
   * <p><b>스프링의 종료 예산({@code spring.lifecycle.timeout-per-shutdown-phase}, 기본 30초)보다 짧아야 한다.</b> 같거나
   * 길면 스프링이 먼저 포기하고 빈 소멸로 넘어가, 아직 도는 폴링 스레드가 닫히는 중인 {@code DataSource}를 건드린다.
   */
  private static final Duration STOP_TIMEOUT = Duration.ofSeconds(20);

  private static final String POLLING_THREAD_NAME = "sqs-event-consumer";

  private final SqsClient eventSqsClient;
  private final SQSProperties sqsProperties;

  /** 적재 시점과 같은 매퍼여야 한다. 날짜 형식 하나만 달라도 왕복이 깨진다. */
  private final ObjectMapper objectMapper;

  private final Map<Class<? extends DomainEvent>, List<EventHandler<DomainEvent>>>
      handlersByEventClass;

  private volatile boolean running;
  private volatile Thread pollingThread;

  /**
   * 등록된 핸들러를 처리 대상 타입으로 묶어 둔다.
   *
   * <p>{@link EventHandler#eventClass()}가 선언한 타입이 곧 분배 기준이라, 소비 시점에 {@code instanceof} 분기가 필요 없다.
   *
   * @param eventHandlers 스프링이 찾은 모든 핸들러. 알림 계열 핸들러도 함께 들어오지만 큐로 오는 이벤트와 타입이 겹치지 않아 호출되지 않는다
   */
  @SuppressWarnings("unchecked")
  public SQSEventConsumer(
      SqsClient eventSqsClient,
      SQSProperties sqsProperties,
      ObjectMapper objectMapper,
      List<EventHandler<? extends DomainEvent>> eventHandlers) {
    this.eventSqsClient = eventSqsClient;
    this.sqsProperties = sqsProperties;
    this.objectMapper = objectMapper;
    this.handlersByEventClass =
        eventHandlers.stream()
            .collect(
                Collectors.groupingBy(
                    EventHandler::eventClass,
                    Collectors.mapping(
                        handler -> (EventHandler<DomainEvent>) handler, Collectors.toList())));
  }

  @Override
  public void start() {
    running = true;
    pollingThread = new Thread(this::pollUntilStopped, POLLING_THREAD_NAME);
    pollingThread.start();
    log.info("SQS 이벤트 소비를 시작했습니다 - queueUrl={}", sqsProperties.queueUrl());
  }

  /**
   * 폴링을 멈추고 스레드가 끝나기를 기다린다.
   *
   * <p>대기 중인 {@code ReceiveMessage} 호출을 끊기 위해 인터럽트를 건다. 이때 SDK는 {@link AbortedException}을 던지고, 폴링
   * 루프는 이를 정상 종료로 다룬다.
   *
   * <p>{@link Thread#join(long)}은 대기 시간이 지나도 예외를 던지지 않는다. 끝난 것과 아직 도는 것을 구분하려면 {@link
   * Thread#isAlive()}를 따로 봐야 한다. 구분하지 않으면 스레드가 남아 닫히는 중인 자원을 건드리는데도 로그에는 정상 종료로 보인다.
   */
  @Override
  public void stop() {
    running = false;
    Thread thread = pollingThread;
    if (thread == null) {
      return;
    }
    thread.interrupt();
    try {
      thread.join(STOP_TIMEOUT.toMillis());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
    pollingThread = null;

    if (thread.isAlive()) {
      log.warn("SQS 이벤트 소비 스레드가 제한 시간 안에 끝나지 않았습니다 - timeout={}s", STOP_TIMEOUT.toSeconds());
      return;
    }
    log.info("SQS 이벤트 소비를 종료했습니다");
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /**
   * 멈추라는 지시가 있을 때까지 큐를 계속 비운다.
   *
   * <p>{@code @Scheduled}는 반복 작업의 예외를 삼키고 다음 주기를 계속 돌려주지만, 직접 만든 스레드는 예외가 밖으로 나가면 <b>그대로 죽고 소비가 영구히
   * 멈춘다.</b> 그래서 {@link Error}까지 잡는다. 복구하려는 것이 아니라 죽었다는 사실을 남기기 위해서다.
   *
   * <p>어떻게 끝나든 {@link #running}을 내린다. 이 값이 {@link #isRunning()}의 답이라, 스레드가 죽었는데 켜져 있으면 스프링과 사람 모두
   * 소비가 도는 줄로 안다.
   */
  private void pollUntilStopped() {
    try {
      while (running) {
        try {
          consumeOnce();
        } catch (AbortedException exception) {
          // stop() 의 인터럽트로 대기 중이던 호출이 끊긴 것이다. 종료 절차의 일부라 남기지 않는다.
          break;
        } catch (RuntimeException exception) {
          log.error("SQS 이벤트 수신에 실패했습니다 - queueUrl={}", sqsProperties.queueUrl(), exception);
          if (!sleepBeforeRetry()) {
            break;
          }
        }
      }
    } catch (Error error) {
      log.error("SQS 이벤트 소비 스레드가 중단되었습니다 - queueUrl={}", sqsProperties.queueUrl(), error);
      throw error;
    } finally {
      running = false;
    }
  }

  /**
   * 큐에서 한 배치를 받아 처리하고, 성공한 메시지만 삭제한다.
   *
   * <p>폴링 루프가 반복해서 부르는 단위다. 스레드를 띄우지 않고 이 메서드만 직접 불러 동작을 검증할 수 있다.
   */
  void consumeOnce() {
    List<Message> messages = receiveMessages();
    if (messages.isEmpty()) {
      return;
    }
    log.debug("SQS 메시지를 수신했습니다 - count={}", messages.size());
    deleteConsumed(consumeBatch(messages));
  }

  private List<Message> receiveMessages() {
    return eventSqsClient
        .receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(sqsProperties.queueUrl())
                .maxNumberOfMessages(sqsProperties.maxMessages())
                .waitTimeSeconds((int) sqsProperties.waitTime().toSeconds())
                .visibilityTimeout((int) sqsProperties.visibilityTimeout().toSeconds())
                // 요청하지 않으면 응답에 실리지 않는다. eventType 이 없으면 되돌릴 타입을,
                // MessageGroupId 가 없으면 실패한 그룹을 막을 기준을 알 수 없다.
                .messageAttributeNames(EVENT_TYPE_ATTRIBUTE)
                .messageSystemAttributeNames(MessageSystemAttributeName.MESSAGE_GROUP_ID)
                .build())
        .messages();
  }

  /**
   * 배치를 순서대로 처리하고 삭제할 메시지를 모은다.
   *
   * <p>실패한 메시지가 속한 그룹은 이번 배치에서 더 처리하지 않는다. 계속 처리하면 앞 이벤트가 재전달돼 다시 처리될 때 이미 뒤 이벤트가 반영돼 있어 같은 그룹 안에서
   * 순서가 역전된다. 건너뛴 메시지도 삭제하지 않으므로 다음 전달 때 순서대로 다시 처리된다.
   *
   * <p>큐가 그룹당 한 번에 하나만 준다고 기대할 수 없다. FIFO 큐는 같은 그룹의 메시지를 한 응답에 순서대로 함께 실어 준다.
   */
  private List<Message> consumeBatch(List<Message> messages) {
    List<Message> consumed = new ArrayList<>();
    Set<String> blockedGroups = new HashSet<>();

    for (Message message : messages) {
      String messageGroupId = message.attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
      if (blockedGroups.contains(messageGroupId)) {
        continue;
      }
      try {
        consume(message);
        consumed.add(message);
      } catch (RuntimeException exception) {
        blockedGroups.add(messageGroupId);
        log.error(
            CRITICAL_MARKER,
            "SQS 이벤트 처리에 실패했습니다 - messageId={}, messageGroupId={}",
            message.messageId(),
            messageGroupId,
            exception);
      }
    }
    return consumed;
  }

  /**
   * 메시지 하나를 이벤트로 되돌려 타입이 맞는 핸들러 전부에게 넘긴다.
   *
   * <p>핸들러가 던진 예외를 잡지 않는다. 삼키면 메시지가 삭제되어 처리되지 않은 이벤트가 사라진다. 핸들러 여러 개 중 하나만 실패해도 메시지 전체가 재전달되므로 이미
   * 성공한 핸들러도 다시 실행된다. 그래서 이 계열의 핸들러는 여러 번 실행돼도 결과가 같아야 한다({@link EventHandler} 참고).
   *
   * @throws IllegalStateException 되돌린 타입을 처리할 핸들러가 없는 경우
   */
  private void consume(Message message) {
    MessageQueueEvent event = toEvent(message);
    List<EventHandler<DomainEvent>> handlers =
        handlersByEventClass.getOrDefault(event.getClass(), List.of());

    if (handlers.isEmpty()) {
      throw new IllegalStateException(
          "이벤트를 처리할 핸들러가 없습니다 - eventType=" + event.eventType() + ", eventId=" + event.eventId());
    }
    for (EventHandler<DomainEvent> handler : handlers) {
      handler.handle(event);
    }
    log.debug(
        "SQS 이벤트 처리를 완료했습니다 - eventId={}, eventType={}, messageId={}",
        event.eventId(),
        event.eventType(),
        message.messageId());
  }

  /**
   * 본문을 {@code eventType} 속성이 가리키는 타입으로 되돌린다.
   *
   * @throws IllegalStateException 속성이 없는 경우
   * @throws IllegalArgumentException 아는 {@link EventType}이 아닌 경우
   */
  private MessageQueueEvent toEvent(Message message) {
    MessageAttributeValue attribute = message.messageAttributes().get(EVENT_TYPE_ATTRIBUTE);
    if (attribute == null || attribute.stringValue() == null) {
      throw new IllegalStateException(
          "메시지에 " + EVENT_TYPE_ATTRIBUTE + " 속성이 없습니다 - messageId=" + message.messageId());
    }
    EventType eventType = EventType.valueOf(attribute.stringValue());
    return objectMapper.readValue(message.body(), eventType.messageQueueEventClass());
  }

  /**
   * 처리에 성공한 메시지를 큐에서 지운다.
   *
   * <p>삭제 실패는 되살리지 않고 남기기만 한다. 그 메시지는 다시 전달되어 한 번 더 처리되는데, 핸들러가 멱등하므로 결과가 달라지지 않는다.
   */
  private void deleteConsumed(List<Message> consumed) {
    if (consumed.isEmpty()) {
      return;
    }

    List<DeleteMessageBatchRequestEntry> entries =
        consumed.stream()
            .map(
                message ->
                    DeleteMessageBatchRequestEntry.builder()
                        .id(message.messageId())
                        .receiptHandle(message.receiptHandle())
                        .build())
            .toList();

    DeleteMessageBatchResponse response =
        eventSqsClient.deleteMessageBatch(
            DeleteMessageBatchRequest.builder()
                .queueUrl(sqsProperties.queueUrl())
                .entries(entries)
                .build());

    if (response.hasFailed() && !response.failed().isEmpty()) {
      // id 만 남기면 권한 문제와 만료된 receiptHandle 을 구분할 수 없다. senderFault 가 true 면
      // 우리 요청이 잘못된 것이고, false 면 큐 쪽 문제라 다시 시도할 여지가 있다.
      log.error(
          "SQS 이벤트 삭제에 실패했습니다 - count={}, failures={}",
          response.failed().size(),
          response.failed().stream().map(SQSEventConsumer::describeFailure).toList());
    }
  }

  private static String describeFailure(BatchResultErrorEntry failed) {
    return "%s(code=%s, senderFault=%s, message=%s)"
        .formatted(failed.id(), failed.code(), failed.senderFault(), failed.message());
  }

  /** 재시도 전 잠시 쉰다. 인터럽트되면 {@code false}를 돌려 폴링 루프를 끝낸다. */
  private boolean sleepBeforeRetry() {
    try {
      Thread.sleep(ERROR_BACKOFF.toMillis());
      return true;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
