package com.ootd.pickup.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auth.kakao.KakaoClient;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.repository.PointRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class KakaoMemberServiceTest {

  @Mock private MemberRepository memberRepository;

  @Mock private PointRepository pointRepository;

  @InjectMocks private KakaoMemberService kakaoMemberService;

  @Test
  void 기존_카카오_회원이면_새로_생성하지_않는다() {
    KakaoClient.KakaoUser user = new KakaoClient.KakaoUser("kakao-subject", null);
    Member existingMember = createMember("기존회원", 1L);
    given(memberRepository.findByOauthProviderAndOauthSubject("KAKAO", user.subject()))
        .willReturn(Optional.of(existingMember));

    KakaoMemberService.KakaoMemberResult result = kakaoMemberService.findOrCreate(user);

    assertThat(result.created()).isFalse();
    assertThat(result.member()).isEqualTo(existingMember);
    then(memberRepository).should(never()).save(any());
    then(pointRepository).should(never()).save(any());
  }

  @Test
  void 신규_카카오_사용자면_랜덤_닉네임으로_회원과_포인트를_생성한다() {
    KakaoClient.KakaoUser user = new KakaoClient.KakaoUser("kakao-subject", "profile.png");
    Member savedMember = createMember("아무닉네임", 10L);
    given(memberRepository.findByOauthProviderAndOauthSubject("KAKAO", user.subject()))
        .willReturn(Optional.empty());
    given(memberRepository.existsByNickname(anyString())).willReturn(false);
    given(memberRepository.save(any(Member.class))).willReturn(savedMember);

    KakaoMemberService.KakaoMemberResult result = kakaoMemberService.findOrCreate(user);

    assertThat(result.created()).isTrue();
    assertThat(result.member()).isEqualTo(savedMember);
    then(memberRepository).should().save(any(Member.class));
    then(pointRepository).should().save(argThat(point -> point.getMemberId().equals(10L)));
  }

  @Test
  void 랜덤_닉네임이_동시_가입으로_선점되면_새_닉네임으로_재시도해_회원을_생성한다() {
    KakaoClient.KakaoUser user = new KakaoClient.KakaoUser("kakao-subject", null);
    Member savedMember = createMember("아무닉네임", 20L);
    given(memberRepository.findByOauthProviderAndOauthSubject("KAKAO", user.subject()))
        .willReturn(Optional.empty());
    given(memberRepository.existsByNickname(anyString())).willReturn(false);
    given(memberRepository.save(any(Member.class)))
        .willThrow(new DataIntegrityViolationException("닉네임 충돌"))
        .willReturn(savedMember);

    KakaoMemberService.KakaoMemberResult result = kakaoMemberService.findOrCreate(user);

    assertThat(result.created()).isTrue();
    assertThat(result.member()).isEqualTo(savedMember);
    then(memberRepository).should(times(2)).save(any(Member.class));
    then(pointRepository).should().save(any());
  }

  @Test
  void 재시도해도_계속_충돌하면_예외를_전파한다() {
    KakaoClient.KakaoUser user = new KakaoClient.KakaoUser("kakao-subject", null);
    given(memberRepository.findByOauthProviderAndOauthSubject("KAKAO", user.subject()))
        .willReturn(Optional.empty());
    given(memberRepository.existsByNickname(anyString())).willReturn(false);
    given(memberRepository.save(any(Member.class)))
        .willThrow(new DataIntegrityViolationException("닉네임 충돌"));

    assertThatThrownBy(() -> kakaoMemberService.findOrCreate(user))
        .isInstanceOf(DataIntegrityViolationException.class);

    then(memberRepository).should(times(3)).save(any(Member.class));
    then(pointRepository).should(never()).save(any());
  }

  private Member createMember(String nickname, Long memberId) {
    Member member = Member.createOAuth("KAKAO", "kakao-subject", nickname, null);
    setMemberId(member, memberId);
    return member;
  }

  private void setMemberId(Member member, Long memberId) {
    try {
      Field memberIdField = Member.class.getDeclaredField("memberId");
      memberIdField.setAccessible(true);
      memberIdField.set(member, memberId);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError(exception);
    }
  }
}
