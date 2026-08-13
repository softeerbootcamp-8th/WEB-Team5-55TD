package com.ootd.pickup.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auth.dto.KakaoLoginRequest;
import com.ootd.pickup.auth.dto.LoginResponseBody;
import com.ootd.pickup.auth.kakao.KakaoClient;
import com.ootd.pickup.auth.token.AccessToken;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class KakaoAuthServiceTest {

  @Mock private KakaoClient kakaoClient;

  @Mock private KakaoMemberService kakaoMemberService;

  @Mock private AuthService authService;

  @InjectMocks private KakaoAuthService kakaoAuthService;

  @Test
  void 카카오_인증에_성공하면_회원_결과로_로그인을_발급한다() {
    KakaoLoginRequest request = new KakaoLoginRequest("auth-code", "https://pickup.test/callback");
    KakaoClient.KakaoUser kakaoUser = new KakaoClient.KakaoUser("kakao-subject", "profile.png");
    Member member = createMember("용감한피카츄07", 1L);
    KakaoMemberService.KakaoMemberResult result =
        new KakaoMemberService.KakaoMemberResult(member, true);
    LoginResponse loginResponse =
        new LoginResponse(
            new LoginResponseBody(1L, "kakao_kakao-subject", member.getNickname(), null, true),
            new AccessToken("access-token", Instant.now().plusSeconds(900)),
            "refresh-token");
    given(kakaoClient.authenticate(request)).willReturn(kakaoUser);
    given(kakaoMemberService.findOrCreate(kakaoUser)).willReturn(result);
    given(authService.issueLogin(member, true)).willReturn(loginResponse);

    LoginResponse response = kakaoAuthService.login(request);

    assertThat(response).isEqualTo(loginResponse);
    then(authService).should().issueLogin(member, true);
  }

  @Test
  void 카카오_인증이_실패하면_예외를_던진다() {
    KakaoLoginRequest request = new KakaoLoginRequest("auth-code", "https://pickup.test/callback");
    given(kakaoClient.authenticate(request)).willThrow(new RestClientException("카카오 서버 오류"));

    assertThatThrownBy(() -> kakaoAuthService.login(request))
        .isInstanceOf(PickUpException.class)
        .hasMessage("카카오 인증에 실패했습니다.");

    then(kakaoMemberService).shouldHaveNoInteractions();
    then(authService).shouldHaveNoInteractions();
  }

  @Test
  void 카카오_인가_코드가_유효하지_않으면_예외를_던진다() {
    KakaoLoginRequest request =
        new KakaoLoginRequest("invalid-code", "https://pickup.test/callback");
    given(kakaoClient.authenticate(request))
        .willThrow(new KakaoClient.KakaoAuthenticationException("인가 코드 검증 실패"));

    assertThatThrownBy(() -> kakaoAuthService.login(request))
        .isInstanceOf(PickUpException.class)
        .hasMessage("카카오 인증에 실패했습니다.");

    then(kakaoMemberService).shouldHaveNoInteractions();
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
