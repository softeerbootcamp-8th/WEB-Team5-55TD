package com.ootd.pickup.global.event.messagequeue.sqs;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * 메시지 큐 이벤트 전송·수신에 쓰는 {@link SqsClient} 설정.
 *
 * <p>{@code event.sqs.enabled=false}(기본값)면 이 설정과 {@link SQSProperties} 가 모두 만들어지지 않는다. 큐가 준비되지 않은
 * 환경에서 접속 정보 없이 앱을 띄우기 위한 것이다. 발행·소비 쪽 빈도 같은 조건으로 꺼진다.
 *
 * <p>따라서 {@code scheduler.outbox.enabled=true} 를 켜려면 이 설정도 함께 켜야 한다. 한쪽만 켜면 릴레이가 주입받을 {@code
 * MessageQueueSender} 가 없어 기동 단계에서 실패한다. 조용히 유실되는 것보다 낫다.
 */
@Configuration
@EnableConfigurationProperties(SQSProperties.class)
@ConditionalOnProperty(name = "event.sqs.enabled", havingValue = "true")
public class SQSConfig {

  /**
   * 롱 폴링 대기가 끝난 뒤 응답을 받아오는 데 쓸 여유.
   *
   * <p><b>호출 제한 시간은 {@code waitTime} 보다 길어야 한다.</b> {@code ReceiveMessage} 는 대기 시간만큼 응답을 붙잡고 있으므로,
   * 고정된 짧은 값을 주면 롱 폴링이 매번 제한 시간에 걸려 끊긴다.
   */
  private static final Duration RESPONSE_MARGIN = Duration.ofSeconds(5);

  /** 한 번의 호출 안에서 SDK 가 재시도할 여유. 호출 전체 제한은 시도 제한보다 이만큼 길다. */
  private static final Duration RETRY_MARGIN = Duration.ofSeconds(10);

  /** LocalStack 등 실 AWS 가 아닌 서버로 접속할 때 쓰는 더미 값. 서명은 필요하지만 검증하는 쪽이 없어 값 자체는 의미가 없다. */
  private static final String LOCAL_DUMMY_CREDENTIAL = "test";

  @Bean(destroyMethod = "close")
  SqsClient eventSqsClient(SQSProperties properties) {
    Duration attemptTimeout = properties.waitTime().plus(RESPONSE_MARGIN);
    SqsClientBuilder builder =
        SqsClient.builder()
            .region(Region.of(properties.region()))
            .httpClientBuilder(UrlConnectionHttpClient.builder().socketTimeout(attemptTimeout))
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apiCallAttemptTimeout(attemptTimeout)
                    .apiCallTimeout(attemptTimeout.plus(RETRY_MARGIN))
                    .build());

    if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
      builder
          .endpointOverride(URI.create(properties.endpoint()))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(LOCAL_DUMMY_CREDENTIAL, LOCAL_DUMMY_CREDENTIAL)));
    }

    return builder.build();
  }

  /**
   * 배치 안에서 서로 다른 메시지 그룹을 동시에 처리할 실행기. 그룹 사이에서만 병렬이고 그룹 안 순서는 {@link SQSGroupBatchProcessor}가 그대로
   * 지킨다.
   *
   * <p>스프링은 {@code SmartLifecycle.stop()}이 전부 끝난 뒤에야 빈을 소멸시키므로, 이 실행기가 닫힐 때는 {@code
   * SQSEventConsumer}가 이미 폴링 스레드를 통해 진행 중이던 그룹 작업까지 기다린 뒤다. 그래서 {@code shutdownNow()}로 강제 종료할 필요 없이
   * {@code shutdown()}만으로 충분하다.
   */
  @Bean(destroyMethod = "shutdown")
  ExecutorService eventSqsGroupExecutor(SQSProperties properties) {
    return Executors.newFixedThreadPool(
        properties.concurrency(), new CustomizableThreadFactory("sqs-event-consumer-worker-"));
  }
}
