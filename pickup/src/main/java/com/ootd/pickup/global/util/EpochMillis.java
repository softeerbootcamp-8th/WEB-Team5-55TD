package com.ootd.pickup.global.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class EpochMillis {

  private EpochMillis() {}

  public static long from(LocalDateTime dateTime) {
    return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  public static LocalDateTime toLocalDateTime(long epochMilli) {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC);
  }
}
