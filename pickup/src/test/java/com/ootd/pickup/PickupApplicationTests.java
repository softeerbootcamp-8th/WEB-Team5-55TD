package com.ootd.pickup;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PickupApplicationTests {

  @Test
  void contextLoads() {}

  @Test
  void WebSocket_통계_MBean을_등록한다() throws MalformedObjectNameException {
    ObjectName objectName =
        new ObjectName("com.ootd.pickup.websocket:name=RealtimeWebSocketMetrics");

    assertThat(ManagementFactory.getPlatformMBeanServer().isRegistered(objectName)).isTrue();
  }
}
