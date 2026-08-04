package com.ootd.pickup.realtime.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AuctionRealtimeMessageTypeTest {

  @Test
  void enum_이름을_JSON_문자열로_직렬화한다() {
    JsonMapper jsonMapper = JsonMapper.builder().build();

    String json = jsonMapper.writeValueAsString(AuctionRealtimeMessageType.BID_PLACED);

    assertThat(json).isEqualTo("\"BID_PLACED\"");
  }
}
