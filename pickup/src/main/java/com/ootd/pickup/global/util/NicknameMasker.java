package com.ootd.pickup.global.util;

public final class NicknameMasker {

  private static final String MASK = "***";

  private NicknameMasker() {}

  /**
   * 닉네임의 첫 글자와 마지막 글자만 노출하고 그 사이는 고정된 "***"로 가린다. 노출 범위를 앞뒤 몇 자처럼 고정 길이로 잡으면 닉네임이 그보다 짧을 때 앞뒤 구간이
   * 겹쳐 사실상 전체가 노출되므로(예: 앞 3자+뒤 2자 방식일 때 "김민주임다" → "김민주***임다"), 길이에 관계없이 항상 앞뒤 1자만 노출한다. 닉네임이 1자거나
   * null/공백이면 노출할 글자가 없어 전체를 가린다.
   */
  public static String mask(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      return MASK;
    }

    int[] codePoints = nickname.codePoints().toArray();
    if (codePoints.length == 1) {
      return MASK;
    }

    String first = new String(codePoints, 0, 1);
    String last = new String(codePoints, codePoints.length - 1, 1);
    return first + MASK + last;
  }
}
