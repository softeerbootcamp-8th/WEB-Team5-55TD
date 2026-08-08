package com.ootd.pickup.cards.domain;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.util.Arrays;
import lombok.Getter;

@Getter
public enum Language {
  ENGLISH("영어"),
  JAPANESE("일본어"),
  KOREAN("한국어");

  private final String displayName;

  Language(String displayName) {
    this.displayName = displayName;
  }

  public static Language from(String language) {
    if (language == null || language.isBlank()) {
      return null;
    }

    return Arrays.stream(values())
        .filter(
            value -> value.name().equalsIgnoreCase(language) || value.displayName.equals(language))
        .findFirst()=
        .orElseThrow(() -> new PickUpException(ILLEGAL_ARGUMENT));
  }

  public String getDisplayName() {
    return displayName;
  }
}
