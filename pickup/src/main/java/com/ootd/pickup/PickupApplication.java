package com.ootd.pickup;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PickupApplication {

  public static void main(String[] args) {
    // JVM 기본 타임존을 배포 환경(Docker TZ, 시스템 설정 등)에 맡기지 않고 여기서 못박는다.
    // 도메인의 LocalDateTime은 전부 UTC 벽시계 값이라는 규약이 어디서 실행되든 성립해야 한다.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(PickupApplication.class, args);
  }
}
