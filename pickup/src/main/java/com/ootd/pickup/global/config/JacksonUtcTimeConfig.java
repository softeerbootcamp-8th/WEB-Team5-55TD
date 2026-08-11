package com.ootd.pickup.global.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * 도메인의 {@code LocalDateTime}은 항상 UTC 벽시계 값이라는 서버 내부 규약을 API 경계에서 명시적으로 드러낸다.
 *
 * <p>도메인 코드는 여전히 시간대 정보가 없는 {@code LocalDateTime}을 쓰지만("서버는 UTC로 통일"), 클라이언트가 이 값을 오해 없이
 * KST로 변환할 수 있어야 하므로 JSON 상에서는 {@code Instant}처럼 {@code Z} 접미사가 붙은 문자열로 주고받는다. 요청 바디도 같은 규약으로
 * 역직렬화한다(예: {@code CreateAuctionRequest.scheduledStartAt}).
 */
@Configuration
public class JacksonUtcTimeConfig {

  @Bean
  public JsonMapperBuilderCustomizer utcLocalDateTimeCustomizer() {
    SimpleModule module = new SimpleModule("PickupUtcLocalDateTime");
    module.addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
    module.addDeserializer(LocalDateTime.class, new UtcLocalDateTimeDeserializer());
    // Spring이 기본 등록하는 날짜/시간 모듈보다 나중에 적용돼야 이 직렬화기가 우선한다.
    return builder -> builder.addModule(module);
  }

  private static class UtcLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {
    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
      gen.writeString(value.toInstant(ZoneOffset.UTC).toString());
    }
  }

  private static class UtcLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {
    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
      String text = p.getString().trim();
      try {
        return LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
      } catch (DateTimeParseException exception) {
        // 오프셋이 없는 값은 이미 UTC 벽시계라는 규약을 그대로 신뢰한다(예: 배치로 들어온 과거 데이터).
        return LocalDateTime.parse(text);
      }
    }
  }
}
