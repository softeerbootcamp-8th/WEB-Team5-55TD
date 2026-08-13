package com.ootd.pickup.auth.service;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RandomNicknameGeneratorTest {

  @Test
  void 생성된_닉네임은_형용사_포켓몬_두자리숫자_형식_8자다() {
    String nickname = RandomNicknameGenerator.generate(candidate -> false);

    assertThat(nickname).hasSize(8);
    assertThat(nickname.substring(6)).matches("\\d{2}");
  }

  @Test
  void 이미_존재하는_닉네임이면_다른_후보로_재시도한다() {
    AtomicInteger callCount = new AtomicInteger();

    String nickname =
        RandomNicknameGenerator.generate(candidate -> callCount.getAndIncrement() < 3);

    assertThat(callCount.get()).isEqualTo(4);
    assertThat(nickname).hasSize(8);
  }

  @Test
  void 모든_후보가_이미_존재하면_예외를_던진다() {
    assertThatThrownBy(() -> RandomNicknameGenerator.generate(candidate -> true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("사용 가능한 랜덤 닉네임을 만들 수 없습니다.");
  }
}
