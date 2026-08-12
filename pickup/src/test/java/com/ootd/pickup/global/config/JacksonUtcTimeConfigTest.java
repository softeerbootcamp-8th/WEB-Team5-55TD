package com.ootd.pickup.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
class JacksonUtcTimeConfigTest {

  private record Sample(LocalDateTime occurredAt) {}

  @Autowired private JsonMapper jsonMapper;

  @Test
  void LocalDateTime_필드는_Z_접미사가_붙은_UTC_문자열로_직렬화된다() {
    // given
    Sample sample = new Sample(LocalDateTime.of(2026, 8, 11, 14, 41, 35));

    // when
    String json = jsonMapper.writeValueAsString(sample);

    // then
    assertThat(json).contains("\"occurredAt\":\"2026-08-11T14:41:35Z\"");
  }

  @Test
  void Z_접미사가_붙은_UTC_문자열을_역직렬화하면_같은_벽시계_값으로_돌아온다() {
    // given
    String json = "{\"occurredAt\":\"2026-08-11T14:41:35Z\"}";

    // when
    Sample sample = jsonMapper.readValue(json, Sample.class);

    // then
    assertThat(sample.occurredAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 14, 41, 35));
  }
}
