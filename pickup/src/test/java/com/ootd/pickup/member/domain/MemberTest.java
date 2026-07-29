package com.ootd.pickup.member.domain;

import static org.assertj.core.api.Assertions.*;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.junit.jupiter.api.Test;

class MemberTest {

  @Test
  void 비밀번호가_없으면_일치하지_않는다() {
    // given
    Member member = Member.create("loginId", null, "nickname");

    // when
    boolean matched = member.isPasswordMatched("anyPassword");

    // then
    assertThat(matched).isFalse();
  }

  @Test
  void 올바른_비밀번호면_일치한다() {
    // given
    String passwordHash = BCrypt.withDefaults().hashToString(12, "rawPassword".toCharArray());
    Member member = Member.create("loginId", passwordHash, "nickname");

    // when
    boolean matched = member.isPasswordMatched("rawPassword");

    // then
    assertThat(matched).isTrue();
  }

  @Test
  void 잘못된_비밀번호면_일치하지_않는다() {
    // given
    String passwordHash = BCrypt.withDefaults().hashToString(12, "rawPassword".toCharArray());
    Member member = Member.create("loginId", passwordHash, "nickname");

    // when
    boolean matched = member.isPasswordMatched("wrongPassword");

    // then
    assertThat(matched).isFalse();
  }
}
