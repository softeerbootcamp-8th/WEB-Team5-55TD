package com.ootd.pickup.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MemberTest {

  @Test
  void 저장된_비밀번호_해시가_없으면_비밀번호가_일치하지_않는다() {
    // given
    Member member = Member.create("pickup-user", null, "픽업회원");

    // when
    boolean isPasswordMatched = member.isPasswordMatched("password1234");

    // then
    assertThat(isPasswordMatched).isFalse();
  }

  @Test
  void 닉네임만_전달하면_나머지_프로필정보는_유지된다() {
    // given
    Member member = Member.create("pickup-user", "old-password-hash", "픽업회원");
    LocalDateTime previousUpdatedAt = member.getUpdatedAt();

    // when
    member.updateProfile("라이츄회원", null, null);

    // then
    assertThat(member.getNickname()).isEqualTo("라이츄회원");
    assertThat(member.getProfileImageUrl()).isNull();
    assertThat(member.getUpdatedAt()).isAfterOrEqualTo(previousUpdatedAt);
  }

  @Test
  void 올바른_비밀번호면_일치한다() {
    // given
    String passwordHash =
        BCrypt.withDefaults().hashToString(12, "rawPassword".toCharArray());
    Member member = Member.create("loginId", passwordHash, "nickname");

    // when
    boolean matched = member.isPasswordMatched("rawPassword");

    // then
    assertThat(matched).isTrue();
  }

  @Test
  void 잘못된_비밀번호면_일치하지_않는다() {
    // given
    String passwordHash =
        BCrypt.withDefaults().hashToString(12, "rawPassword".toCharArray());
    Member member = Member.create("loginId", passwordHash, "nickname");

    // when
    boolean matched = member.isPasswordMatched("wrongPassword");

    // then
    assertThat(matched).isFalse();
  }
}