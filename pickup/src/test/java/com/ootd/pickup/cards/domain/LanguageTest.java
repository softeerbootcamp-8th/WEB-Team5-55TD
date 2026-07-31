package com.ootd.pickup.cards.domain;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;

class LanguageTest {

  @Test
  void null이면_null을_반환한다() {
    // when
    Language language = Language.from(null);

    // then
    assertThat(language).isNull();
  }

  @Test
  void 공백_문자열이면_null을_반환한다() {
    // when
    Language language = Language.from("   ");

    // then
    assertThat(language).isNull();
  }

  @Test
  void 영문_이름으로_대소문자_구분없이_조회하면_해당_언어를_반환한다() {
    // when
    Language language = Language.from("english");

    // then
    assertThat(language).isEqualTo(Language.ENGLISH);
  }

  @Test
  void 표시명으로_조회하면_해당_언어를_반환한다() {
    // when
    Language language = Language.from("한국어");

    // then
    assertThat(language).isEqualTo(Language.KOREAN);
  }

  @Test
  void 지원하지_않는_언어면_예외가_발생한다() {
    // when & then
    assertThatThrownBy(() -> Language.from("중국어")).isInstanceOf(PickUpException.class);
  }
}
