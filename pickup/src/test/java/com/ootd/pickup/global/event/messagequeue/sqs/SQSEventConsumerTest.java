package com.ootd.pickup.global.event.messagequeue.sqs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * {@code receiveMessage}/{@code deleteMessageBatch} 호출 형태와, 그룹 처리 결과에 따라 삭제 여부가 갈리는지만 검증한다. 메시지를
 * 이벤트로 되돌리는 로직은 {@link SQSMessageDispatcherTest}, 그룹별 병렬 처리는 {@link SQSGroupBatchProcessorTest}에서
 * 검증한다.
 */
class SQSEventConsumerTest {

  private static final String QUEUE_URL =
      "https://sqs.ap-northeast-2.amazonaws.com/123456789012/pickup-event.fifo";

  private SqsClient eventSqsClient;
  private SQSGroupBatchProcessor batchProcessor;

  @BeforeEach
  void 소비할_큐를_준비한다() {
    eventSqsClient = mock(SqsClient.class);
    batchProcessor = mock(SQSGroupBatchProcessor.class);
    given(eventSqsClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
        .willReturn(DeleteMessageBatchResponse.builder().build());
  }

  private SQSEventConsumer consumer() {
    SQSProperties properties =
        new SQSProperties(
            QUEUE_URL,
            "ap-northeast-2",
            null,
            Duration.ofSeconds(20),
            Duration.ofSeconds(30),
            10,
            4);
    return new SQSEventConsumer(eventSqsClient, properties, batchProcessor);
  }

  @Test
  void 설정한_폴링_값으로_큐에서_메시지를_받아온다() {
    // given
    givenMessages();

    // when
    consumer().consumeOnce();

    // then
    ArgumentCaptor<ReceiveMessageRequest> captor =
        ArgumentCaptor.forClass(ReceiveMessageRequest.class);
    then(eventSqsClient).should().receiveMessage(captor.capture());
    ReceiveMessageRequest request = captor.getValue();
    assertThat(request.queueUrl()).isEqualTo(QUEUE_URL);
    assertThat(request.maxNumberOfMessages()).isEqualTo(10);
    assertThat(request.waitTimeSeconds()).isEqualTo(20);
    assertThat(request.visibilityTimeout()).isEqualTo(30);
  }

  @Test
  void 되돌릴_타입과_그룹을_알_수_있도록_속성을_함께_요청한다() {
    // given — 요청하지 않으면 응답에 실리지 않는다
    givenMessages();

    // when
    consumer().consumeOnce();

    // then
    ArgumentCaptor<ReceiveMessageRequest> captor =
        ArgumentCaptor.forClass(ReceiveMessageRequest.class);
    then(eventSqsClient).should().receiveMessage(captor.capture());
    assertThat(captor.getValue().messageAttributeNames()).contains("eventType");
    assertThat(captor.getValue().messageSystemAttributeNames())
        .contains(MessageSystemAttributeName.MESSAGE_GROUP_ID);
  }

  @Test
  void 그룹_처리_결과로_받은_메시지를_큐에서_지운다() {
    // given
    Message message = message("message-1");
    givenMessages(message);
    given(batchProcessor.process(anyList())).willReturn(List.of(message));

    // when
    consumer().consumeOnce();

    // then
    assertThat(deletedMessageIds()).containsExactly("message-1");
  }

  @Test
  void 그룹_처리_결과가_비어있으면_삭제를_요청하지_않는다() {
    // given — 실패했거나 핸들러가 없어 결과에서 빠진 메시지는 삭제하면 안 된다
    givenMessages(message("message-1"));
    given(batchProcessor.process(anyList())).willReturn(List.of());

    // when
    consumer().consumeOnce();

    // then
    then(eventSqsClient).should(never()).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
  }

  @Test
  void 받은_메시지가_없으면_그룹_처리기를_부르지_않는다() {
    // given
    givenMessages();

    // when
    consumer().consumeOnce();

    // then
    then(batchProcessor).should(never()).process(anyList());
    then(eventSqsClient).should(never()).deleteMessageBatch(any(DeleteMessageBatchRequest.class));
  }

  private void givenMessages(Message... messages) {
    given(eventSqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .willReturn(ReceiveMessageResponse.builder().messages(messages).build());
  }

  private List<String> deletedMessageIds() {
    ArgumentCaptor<DeleteMessageBatchRequest> captor =
        ArgumentCaptor.forClass(DeleteMessageBatchRequest.class);
    then(eventSqsClient).should().deleteMessageBatch(captor.capture());
    return captor.getValue().entries().stream().map(DeleteMessageBatchRequestEntry::id).toList();
  }

  private Message message(String messageId) {
    return Message.builder().messageId(messageId).receiptHandle("receipt-" + messageId).build();
  }
}
