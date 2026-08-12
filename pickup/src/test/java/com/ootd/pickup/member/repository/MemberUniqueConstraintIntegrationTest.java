package com.ootd.pickup.member.repository;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.member.domain.Member;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 경합에서 어느 값이 겹쳤는지는 위반된 유니크 제약 이름으로 구분한다. 그 이름이 실제 스키마에 붙어 있는지 확인한다 — 이름이 빠지면 구분 로직이 조용히 무력화된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberUniqueConstraintIntegrationTest {

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Test
  void 닉네임이_겹치면_닉네임_유니크_제약_이름을_담은_예외가_발생한다() {
    // given
    memberJpaRepository.saveAndFlush(Member.create("pickup-user", "hash", "픽업회원"));

    // when & then
    assertThatThrownBy(
            () -> memberJpaRepository.saveAndFlush(Member.create("pickup-other", "hash", "픽업회원")))
        .isInstanceOf(DataIntegrityViolationException.class)
        .cause()
        .isInstanceOf(ConstraintViolationException.class)
        .extracting(violation -> ((ConstraintViolationException) violation).getConstraintName())
        .asString()
        .containsIgnoringCase("nickname");
  }

  @Test
  void 아이디가_겹치면_아이디_유니크_제약_이름을_담은_예외가_발생한다() {
    // given
    memberJpaRepository.saveAndFlush(Member.create("pickup-user", "hash", "픽업회원"));

    // when & then
    assertThatThrownBy(
            () -> memberJpaRepository.saveAndFlush(Member.create("pickup-user", "hash", "다른회원")))
        .isInstanceOf(DataIntegrityViolationException.class)
        .cause()
        .isInstanceOf(ConstraintViolationException.class)
        .extracting(violation -> ((ConstraintViolationException) violation).getConstraintName())
        .asString()
        .containsIgnoringCase("login_id");
  }
}
