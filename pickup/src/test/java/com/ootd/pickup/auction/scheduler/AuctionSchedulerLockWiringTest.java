package com.ootd.pickup.auction.scheduler;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code @Scheduled}로 발동될 때 ShedLock 잠금이 실제로 걸리는지 확인한다.
 *
 * <p>다른 테스트는 스케줄러 메서드를 직접 호출하므로 작업 등록 경로를 지나가지 않는다. {@code @Scheduled}, {@code @SchedulerLock},
 * {@code @Transactional} 세 개가 한 메서드에 걸려 있어 조합이 어긋날 여지가 있는데, 어긋나면 여러 인스턴스가 같은 경매를 중복 처리한다. 단일 인스턴스
 * 테스트로는 드러나지 않는 종류라 잠금이 걸리는 것 자체를 확인한다.
 *
 * <p>주기를 1시간으로 둔 이유는 격리다. 스프링은 컨텍스트를 캐시하므로 짧은 주기로 두면 이 클래스가 끝난 뒤에도 스케줄러가 계속 돌면서, JVM 안에서 공유되는
 * H2({@code DB_CLOSE_DELAY=-1})의 경매 데이터를 건드린다. 1시간이면 시작 직후 한 번만 실행되고 조용해진다.
 *
 * <p>{@code @DirtiesContext}로 컨텍스트를 닫는 방법은 쓸 수 없다. {@code ddl-auto: create-drop}의 drop 단계가 실행되어 공유
 * H2의 테이블이 사라지고, 이미 캐시된 다른 컨텍스트가 깨진다.
 */
@SpringBootTest(
    properties = {
      "scheduler.enabled=true",
      "scheduler.auction.enabled=true",
      "scheduler.auction.fixed-delay=1h",
      "spring.sql.init.schema-locations=classpath:db/shedlock-h2.sql"
    })
@ActiveProfiles("test")
class AuctionSchedulerLockWiringTest {

  private static final String LOCK_NAME = "auction-status-transition";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ScheduledTaskHolder scheduledTaskHolder;

  @Test
  void 상태_전이_작업이_스케줄에_등록된다() {
    // when
    boolean registered =
        scheduledTaskHolder.getScheduledTasks().stream()
            .map(task -> task.getTask().toString())
            .anyMatch(description -> description.contains("transitionDueAuctions"));

    // then
    assertThat(registered).isTrue();
  }

  @Test
  void 작업이_발동되면_잠금_행이_생긴다() {
    // given — 컨텍스트가 뜨면서 작업이 한 번 발동된다

    // when & then
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              Integer count =
                  jdbcTemplate.queryForObject(
                      "select count(*) from shedlock where name = ?", Integer.class, LOCK_NAME);
              assertThat(count).isEqualTo(1);
            });

    String lockedBy =
        jdbcTemplate.queryForObject(
            "select locked_by from shedlock where name = ?", String.class, LOCK_NAME);
    assertThat(lockedBy).isNotBlank();
  }
}
