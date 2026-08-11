package com.ootd.pickup.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NicknameMaskerTest {

  @Test
  void 닉네임의_첫_글자와_마지막_글자만_노출한다() {
    // when & then
    assertThat(NicknameMasker.mask("귀염사쿠")).isEqualTo("귀***쿠");
  }

  @Test
  void 닉네임이_길어도_앞뒤_1자만_노출한다() {
    // when & then
    assertThat(NicknameMasker.mask("김민주임다")).isEqualTo("김***다");
  }

  @Test
  void 닉네임이_null이거나_공백이면_전체를_가린다() {
    // when & then
    assertThat(NicknameMasker.mask(null)).isEqualTo("***");
    assertThat(NicknameMasker.mask(" ")).isEqualTo("***");
  }

  @Test
  void 한_글자_닉네임은_전체를_가린다() {
    // when & then
    assertThat(NicknameMasker.mask("피")).isEqualTo("***");
  }

  @Test
  void 두_글자_닉네임은_첫_글자와_마지막_글자를_노출한다() {
    // when & then
    assertThat(NicknameMasker.mask("피카")).isEqualTo("피***카");
  }

  @Test
  void 이모지가_포함되어도_문자가_깨지지_않는다() {
    // when & then
    assertThat(NicknameMasker.mask("😀피카츄😺")).isEqualTo("😀***😺");
  }
}
