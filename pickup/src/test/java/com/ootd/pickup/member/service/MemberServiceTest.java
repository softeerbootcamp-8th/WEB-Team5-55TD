package com.ootd.pickup.member.service;

import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_NOT_FOUND;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.dto.MemberRequest;
import com.ootd.pickup.member.dto.MemberResponse;
import com.ootd.pickup.member.dto.MyProfileResponse;
import com.ootd.pickup.member.dto.PointBalanceResponse;
import com.ootd.pickup.member.dto.UpdateMyProfileRequest;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock private MemberRepository memberRepository;

  @Mock private MemberManageService memberManageService;

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

  @Test
  void 존재하는_회원정보를_조회하면_내_정보를_반환한다() {
    // given
    Member member = Member.create("pickup-user", "password-hash", "픽업회원");
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when
    MyProfileResponse response = memberService.getMyProfile(1L);

    // then
    assertThat(response.loginId()).isEqualTo("pickup-user");
    assertThat(response.nickname()).isEqualTo("픽업회원");
    assertThat(response.profileImageUrl()).isNull();
  }

  @Test
  void 닉네임만_수정하면_다른_회원정보는_유지된다() {
    // given
    Member member = Member.create("pickup-user", "password-hash", "픽업회원");
    UpdateMyProfileRequest request = new UpdateMyProfileRequest("라이츄회원", null, null, null);
    given(memberManageService.getMemberById(1L)).willReturn(member);
    given(memberRepository.existsByNickname("라이츄회원")).willReturn(false);

    // when
    MyProfileResponse response = memberService.updateMyProfile(1L, request);

    // then
    assertThat(response.nickname()).isEqualTo("라이츄회원");
    assertThat(response.loginId()).isEqualTo("pickup-user");
    assertThat(readPasswordHash(member)).isEqualTo("password-hash");
  }

  @Test
  void 비밀번호를_수정하면_BCrypt_해시로_저장된다() {
    // given
    String currentPassword = "old-password";
    Member member = Member.create("pickup-user", hashPassword(currentPassword), "픽업회원");
    UpdateMyProfileRequest request =
        new UpdateMyProfileRequest(null, currentPassword, "new-password", null);
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when
    memberService.updateMyProfile(1L, request);

    // then
    assertThat(readPasswordHash(member)).isNotEqualTo(request.password());
    assertThat(
            BCrypt.verifyer()
                .verify(request.password().toCharArray(), readPasswordHash(member))
                .verified)
        .isTrue();
  }

  @Test
  void 현재비밀번호가_일치하지_않으면_비밀번호를_변경하지_않는다() {
    // given
    String passwordHash = hashPassword("old-password");
    Member member = Member.create("pickup-user", passwordHash, "픽업회원");
    UpdateMyProfileRequest request =
        new UpdateMyProfileRequest(null, "wrong-password", "new-password", null);
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when & then
    assertThatThrownBy(() -> memberService.updateMyProfile(1L, request))
        .isInstanceOf(PickUpException.class)
        .hasMessage("비밀번호가 일치하지 않습니다.");
    assertThat(readPasswordHash(member)).isEqualTo(passwordHash);
  }

  @Test
  void 현재비밀번호가_일치하지_않으면_닉네임과_비밀번호를_모두_변경하지_않는다() {
    // given
    String passwordHash = hashPassword("old-password");
    Member member = Member.create("pickup-user", passwordHash, "픽업회원");
    UpdateMyProfileRequest request =
        new UpdateMyProfileRequest("라이츄회원", "wrong-password", "new-password", null);
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when & then
    assertThatThrownBy(() -> memberService.updateMyProfile(1L, request))
        .isInstanceOf(PickUpException.class)
        .hasMessage("비밀번호가 일치하지 않습니다.");
    assertThat(member.getNickname()).isEqualTo("픽업회원");
    assertThat(readPasswordHash(member)).isEqualTo(passwordHash);
    then(memberRepository).should(never()).existsByNickname(anyString());
  }

  @Test
  void 프로필이미지URL만_수정하면_새_URL을_반환한다() {
    // given
    Member member = Member.create("pickup-user", "password-hash", "픽업회원");
    UpdateMyProfileRequest request =
        new UpdateMyProfileRequest(null, null, null, "https://example.com/profile.png");
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when
    MyProfileResponse response = memberService.updateMyProfile(1L, request);

    // then
    assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profile.png");
  }

  @Test
  void 이미_사용중인_닉네임으로_수정하면_409_예외를_던진다() {
    // given
    Member member = Member.create("pickup-user", "password-hash", "픽업회원");
    UpdateMyProfileRequest request = new UpdateMyProfileRequest("라이츄회원", null, null, null);
    given(memberManageService.getMemberById(1L)).willReturn(member);
    given(memberRepository.existsByNickname("라이츄회원")).willReturn(true);

    // when & then
    assertThatThrownBy(() -> memberService.updateMyProfile(1L, request))
        .isInstanceOf(PickUpException.class)
        .hasMessage("이미 사용 중인 닉네임입니다.");
  }

  @Test
  void 존재하는_회원의_포인트를_조회하면_잔액을_반환한다() {
    // given
    Point point = Point.create(1L);
    given(pointRepository.findByMemberId(1L)).willReturn(Optional.of(point));

    // when
    PointBalanceResponse response = memberService.getMyPointBalance(1L);

    // then
    assertThat(response.pointBalance()).isZero();
  }

  @Test
  void 포인트정보가_없으면_404_예외를_던진다() {
    // given
    given(pointRepository.findByMemberId(1L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> memberService.getMyPointBalance(1L))
        .isInstanceOf(PickUpException.class)
        .hasMessage("회원을 찾을 수 없습니다.");
  }

  @Test
  void 존재하지_않는_회원정보를_조회하면_404_예외를_던진다() {
    // given
    given(memberManageService.getMemberById(1L)).willThrow(new PickUpException(MEMBER_NOT_FOUND));

    // when & then
    assertThatThrownBy(() -> memberService.getMyProfile(1L))
        .isInstanceOf(PickUpException.class)
        .hasMessage("회원을 찾을 수 없습니다.");
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

  private String hashPassword(String rawPassword) {
    return BCrypt.withDefaults().hashToString(4, rawPassword.toCharArray());
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
