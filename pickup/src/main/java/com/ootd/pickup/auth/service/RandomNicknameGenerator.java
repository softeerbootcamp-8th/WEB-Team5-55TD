package com.ootd.pickup.auth.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class RandomNicknameGenerator {
  private static final List<String> ADJECTIVES =
      List.of("용감한", "신나는", "상냥한", "재빠른", "영리한", "씩씩한", "행복한", "다정한");
  private static final List<String> POKEMON =
      List.of("피카츄", "꼬부기", "파이리", "이브이", "메타몽", "토게피", "리자몽", "잠만보");

  private RandomNicknameGenerator() {}

  public static String generate(Predicate<String> alreadyExists) {
    for (int attempt = 0; attempt < 1_000; attempt++) {
      String nickname = randomCandidate();
      if (!alreadyExists.test(nickname)) {
        return nickname;
      }
    }
    throw new IllegalStateException("사용 가능한 랜덤 닉네임을 만들 수 없습니다.");
  }

  private static String randomCandidate() {
    String adjective = ADJECTIVES.get(ThreadLocalRandom.current().nextInt(ADJECTIVES.size()));
    String pokemon = POKEMON.get(ThreadLocalRandom.current().nextInt(POKEMON.size()));
    int number = ThreadLocalRandom.current().nextInt(100);
    return adjective + pokemon + String.format("%02d", number);
  }
}
