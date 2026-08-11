package com.ootd.pickup.cards.domain;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;

class RarityTest {

  @Test
  void null이면_null을_반환한다() {
    // when
    Rarity rarity = Rarity.from(null);

    // then
    assertThat(rarity).isNull();
  }

  @Test
  void 공백_문자열이면_null을_반환한다() {
    // when
    Rarity rarity = Rarity.from("   ");

    // then
    assertThat(rarity).isNull();
  }

  @Test
  void 영문_이름으로_대소문자_구분없이_조회하면_해당_레어도를_반환한다() {
    // when
    Rarity rarity = Rarity.from("rare_holo");

    // then
    assertThat(rarity).isEqualTo(Rarity.RARE_HOLO);
  }

  @Test
  void 표시명으로_조회하면_해당_레어도를_반환한다() {
    // when
    Rarity rarity = Rarity.from("레어 홀로");

    // then
    assertThat(rarity).isEqualTo(Rarity.RARE_HOLO);
  }

  @Test
  void 지원하지_않는_레어도면_예외가_발생한다() {
    // when & then
    assertThatThrownBy(() -> Rarity.from("시크릿 레어")).isInstanceOf(PickUpException.class);
  }

  @Test
  void EX_GX_V_세대의_홀로_레어도도_표시명으로_조회할_수_있다() {
    // when & then
    assertThat(Rarity.from("레어 홀로 EX")).isEqualTo(Rarity.RARE_HOLO_EX);
    assertThat(Rarity.from("레어 홀로 GX")).isEqualTo(Rarity.RARE_HOLO_GX);
    assertThat(Rarity.from("레어 홀로 V")).isEqualTo(Rarity.RARE_HOLO_V);
    assertThat(Rarity.from("레어 홀로 VMAX")).isEqualTo(Rarity.RARE_HOLO_VMAX);
    assertThat(Rarity.from("레어 홀로 VSTAR")).isEqualTo(Rarity.RARE_HOLO_VSTAR);
  }
}
