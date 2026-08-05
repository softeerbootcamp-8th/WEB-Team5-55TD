package com.ootd.pickup.global.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러 활성화와 인스턴스 간 중복 실행 방지 설정.
 *
 * <p>인스턴스를 여러 대로 늘려도 각 작업이 한 번만 돌아야 하므로 ShedLock으로 실행권을 나눈다. 낙찰 처리처럼 두 번 돌면 안 되는 후속 작업이 이 잠금에 기대고
 * 있다.
 *
 * <p>{@code scheduler.enabled=false}면 이 설정 자체가 만들어지지 않아 {@link EnableScheduling}도, 잠금 AOP도, {@link
 * LockProvider}도 없다. 테스트에서 1초 주기 작업이 도는 것을 막는 데 쓴다.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(
    defaultLockAtMostFor = SchedulerConfig.DEFAULT_LOCK_AT_MOST_FOR,
    // 잠금 AOP 를 트랜잭션 AOP 보다 반드시 바깥에 둔다. 둘의 기본 order 가 모두
    // Ordered.LOWEST_PRECEDENCE 라 명시하지 않으면 어느 쪽이 바깥인지 보장되지 않는다.
    // 잠금이 안쪽으로 들어가면 업무 커넥션을 쥔 채 잠금용 커넥션을 하나 더 잡게 된다.
    order = Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {

  /** 인스턴스가 죽어 잠금을 놓지 못했을 때의 최대 점유 시간. 각 작업에서 재정의할 수 있다. */
  static final String DEFAULT_LOCK_AT_MOST_FOR = "PT30S";

  /**
   * JDBC 기반 잠금 저장소.
   *
   * <p>Redis 대신 DB를 고른 이유는 잠금과 Outbox 적재가 같은 저장소에 놓여 신뢰 경계가 하나로 묶이기 때문이다. Redis 장애 시 잠금이 풀려 중복
   * 실행되면, 하필 중복이 가장 치명적인 낙찰 처리가 두 번 돈다.
   *
   * <p>{@code usingDbTime()}은 잠금 만료 판정을 DB 시각으로 하게 한다. 인스턴스마다 시계가 조금씩 어긋나도 만료 시점 해석이 갈라지지 않는다.
   *
   * @param dataSource 애플리케이션과 같은 데이터소스
   * @return {@code shedlock} 테이블을 쓰는 잠금 제공자
   */
  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }
}
