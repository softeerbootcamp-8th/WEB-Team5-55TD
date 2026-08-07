package com.ootd.pickup.global.util;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public final class CursorCodec {

  private static final String DELIMITER = "\\|";

  private CursorCodec() {}

  public static String encode(Object... parts) {
    String raw = Arrays.stream(parts).map(String::valueOf).collect(Collectors.joining("|"));
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  public static <T> T decode(String cursor, Function<String[], T> parser) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }

    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      return parser.apply(raw.split(DELIMITER, -1));
    } catch (RuntimeException e) {
      throw new PickUpException(INVALID_CURSOR);
    }
  }
}
