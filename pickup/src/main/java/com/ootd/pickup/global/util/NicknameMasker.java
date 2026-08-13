package com.ootd.pickup.global.util;

public final class NicknameMasker {

  private NicknameMasker() {}

  public static String mask(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      return "***";
    }

    int[] codePoints = nickname.codePoints().toArray();
    if (codePoints.length == 1) {
      return "***";
    }

    String first = new String(codePoints, 0, 1);
    String last = new String(codePoints, codePoints.length - 1, 1);
    return first + "***" + last;
  }
}
