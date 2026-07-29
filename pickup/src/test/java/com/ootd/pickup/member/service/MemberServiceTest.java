package com.ootd.pickup.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.dto.MemberRequest;
import com.ootd.pickup.member.dto.MemberResponse;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock private MemberRepository memberRepository;

  @Mock private PointRepository pointRepository;

  @InjectMocks private MemberService memberService;

  @Test
  void 중복되지_않은_회원정보로_회원을_생성한다() {
    // given
    MemberRequest request = new MemberRequest("pickup-user", "픽업회원", "password1234");
    given(memberRepository.existsByLoginId(request.loginId())).willReturn(false);
    given(memberRepository.existsByNickname(request.nickname())).willReturn(false);
    given(memberRepository.save(any(Member.class)))
        .willAnswer(
            invocation -> {
              Member member = invocation.getArgument(0);
              writeMemberId(member, 1L);
              return member;
            });

    // when
    MemberResponse response = memberService.createMember(request);

    // then
    assertThat(response.loginId()).isEqualTo(request.loginId());
    assertThat(response.nickname()).isEqualTo(request.nickname());

    ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
    then(memberRepository).should().save(memberCaptor.capture());
    assertThat(memberCaptor.getValue()).isNotNull();

    assertThat(readPasswordHash(memberCaptor.getValue())).isNotEqualTo(request.password());
    assertThat(
            BCrypt.verifyer()
                .verify(request.password().toCharArray(), readPasswordHash(memberCaptor.getValue()))
                .verified)
        .isTrue();

    ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);
    then(pointRepository).should().save(pointCaptor.capture());
    assertThat(pointCaptor.getValue().getMemberId()).isEqualTo(1L);
    assertThat(pointCaptor.getValue().getBalance()).isZero();
  }

  @Test
  void 아이디가_중복되면_회원을_생성하지_않는다() {
    // given
    MemberRequest request = new MemberRequest("pickup-user", "픽업회원", "password1234");
    given(memberRepository.existsByLoginId(request.loginId())).willReturn(true);

    // when & then
    assertThatThrownBy(() -> memberService.createMember(request))
        .isInstanceOf(PickUpException.class)
        .hasMessage("이미 사용 중인 아이디입니다.");

    verify(memberRepository, never()).save(any(Member.class));
  }

  @Test
  void 닉네임이_중복되면_회원을_생성하지_않는다() {
    // given
    MemberRequest request = new MemberRequest("pickup-user", "픽업회원", "password1234");
    given(memberRepository.existsByLoginId(request.loginId())).willReturn(false);
    given(memberRepository.existsByNickname(request.nickname())).willReturn(true);

    // when & then
    assertThatThrownBy(() -> memberService.createMember(request))
        .isInstanceOf(PickUpException.class)
        .hasMessage("이미 사용 중인 닉네임입니다.");

    verify(memberRepository, never()).save(any(Member.class));
  }

  private String readPasswordHash(Member member) {
    try {
      Field passwordField = Member.class.getDeclaredField("password");
      passwordField.setAccessible(true);
      return (String) passwordField.get(member);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError(exception);
    }
  }

  private void writeMemberId(Member member, Long memberId) {
    try {
      Field memberIdField = Member.class.getDeclaredField("memberId");
      memberIdField.setAccessible(true);
      memberIdField.set(member, memberId);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError(exception);
    }
  }
}
