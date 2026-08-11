package com.ootd.pickup.cards.domain;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.util.Arrays;
import lombok.Getter;

@Getter
public enum Rarity {
  COMMON("커먼"),
  UNCOMMON("언커먼"),
  RARE("레어"),
  RARE_HOLO("레어 홀로");

  private final String displayName;

  Rarity(String displayName) {
    this.displayName = displayName;
  }

  public static Rarity from(String rarity) {
    if (rarity == null || rarity.isBlank()) {
      return null;
    }

    return Arrays.stream(values())
        .filter(value -> value.name().equalsIgnoreCase(rarity) || value.displayName.equals(rarity))
        .findFirst()
        .orElseThrow(() -> new PickUpException(ILLEGAL_ARGUMENT));
  }
}
