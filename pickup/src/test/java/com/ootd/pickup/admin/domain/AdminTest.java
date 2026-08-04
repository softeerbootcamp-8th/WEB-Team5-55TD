package com.ootd.pickup.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.junit.jupiter.api.Test;

class AdminTest {

  @Test
  void 올바른_비밀번호면_일치한다() {
    // given
    String passwordHash = BCrypt.withDefaults().hashToString(12, "rawPassword".toCharArray());
    Admin admin = Admin.create("admin", passwordHash, "관리자");

    // when
    boolean matched = admin.isPasswordMatched("rawPassword");

    // then
    assertThat(matched).isTrue();
  }

  @Test
  void 잘못된_비밀번호면_일치하지_않는다() {
    // given
    String passwordHash = BCrypt.withDefaults().hashToString(12, "rawPassword".toCharArray());
    Admin admin = Admin.create("admin", passwordHash, "관리자");

    // when
    boolean matched = admin.isPasswordMatched("wrongPassword");

    // then
    assertThat(matched).isFalse();
  }
}
