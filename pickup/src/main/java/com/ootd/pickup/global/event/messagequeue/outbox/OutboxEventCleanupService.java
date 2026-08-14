package com.ootd.pickup.global.event.messagequeue.outbox;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 발행이 끝난 Outbox 행을 보존 기간이 지나면 지운다.
 *
 * <p><b>{@code published=false}인 행은 절대 지우지 않는다.</b> 발행에 계속 실패해 남아 있는 행(poison message)까지 보존 기간이
 * 지났다는 이유로 지우면 아직 큐에 들어가지 않은 이벤트가 그대로 유실된다.
 *
 * <p>한 번에 전부 지우지 않고 {@link OutboxEventCleanupProperties#batchSize()}건씩 나눠 지운다. 대상 전체를 한 트랜잭션으로 지우면
 * 그만큼 락/undo log/binlog를 오래 붙잡아 동시에 쓰는 다른 트랜잭션을 막는다. 배치를 반복하다 {@link
 * OutboxEventCleanupProperties#maxDeletesPerRun()}에 닿으면 이번 호출은 멈추고, 남은 행은 다음 주기가 이어서 지운다.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(OutboxEventCleanupProperties.class)
public class OutboxEventCleanupService {

  private final OutboxEventRepository outboxEventRepository;
  private final OutboxEventCleanupProperties outboxEventCleanupProperties;

  /**
   * 보존 기간이 지난 발행 완료 행을 지운다.
   *
   * @return 이번 호출에서 지운 건수
   */
  public int deleteExpiredEvents() {
    LocalDateTime threshold =
        LocalDateTime.now(ZoneOffset.UTC).minusDays(outboxEventCleanupProperties.retentionDays());
    int maxDeletesPerRun = outboxEventCleanupProperties.maxDeletesPerRun();
    int configuredBatchSize = outboxEventCleanupProperties.batchSize();

    int totalDeleted = 0;
    while (totalDeleted < maxDeletesPerRun) {
      // 남은 허용량이 배치 크기보다 작을 수 있다. 그대로 배치 크기만큼 조회하면 이번 호출이
      // maxDeletesPerRun을 넘겨 지운다.
      int remaining = maxDeletesPerRun - totalDeleted;
      Limit batchSize = Limit.of(Math.min(configuredBatchSize, remaining));

      List<String> ids =
          outboxEventRepository.findAllByPublishedTrueAndCreatedAtBefore(threshold, batchSize);
      if (ids.isEmpty()) {
        break;
      }
      totalDeleted += outboxEventRepository.deleteByIdIn(ids);
    }
    return totalDeleted;
  }
}
