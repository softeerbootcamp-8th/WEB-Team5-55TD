package com.ootd.pickup.global.config;

import static org.assertj.core.api.Assertions.*;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

class SchedulerConfigTest {

  @Nested
  @SpringBootTest(
      properties = {
        "scheduler.enabled=true",
        // 설정 배선만 확인한다. 작업이 실제로 돌면 H2 에 없는 shedlock 테이블을 건드린다.
        "scheduler.auction.enabled=false"
      })
  @ActiveProfiles("test")
  class 스케줄러가_켜진_경우 {

    @Autowired private ApplicationContext context;

    @Test
    void ShedLock_잠금_제공자가_등록된다() {
      // when
      LockProvider lockProvider = context.getBean(LockProvider.class);

      // then
      assertThat(lockProvider).isInstanceOf(JdbcTemplateLockProvider.class);
    }
  }

  @Nested
  @SpringBootTest
  @ActiveProfiles("test")
  class 스케줄러가_꺼진_경우 {

    @Autowired private ApplicationContext context;

    @Test
    void 잠금_제공자가_등록되지_않는다() {
      // when & then
      assertThat(context.getBeanNamesForType(LockProvider.class)).isEmpty();
    }
  }
}
