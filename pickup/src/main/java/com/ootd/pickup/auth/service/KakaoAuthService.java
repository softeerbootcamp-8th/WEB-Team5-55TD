package com.ootd.pickup.auth.service;

import static com.ootd.pickup.global.exception.ExceptionCode.KAKAO_AUTHENTICATION_FAILED;

import com.ootd.pickup.auth.dto.KakaoLoginRequest;
import com.ootd.pickup.auth.kakao.KakaoClient;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class KakaoAuthService {
  private static final String PROVIDER = "KAKAO";
  private final KakaoClient kakaoClient;
  private final KakaoMemberService kakaoMemberService;
  private final AuthService authService;

  public LoginResponse login(KakaoLoginRequest request) {
    KakaoClient.KakaoUser kakaoUser;
    try {
      kakaoUser = kakaoClient.authenticate(request);
    } catch (RestClientException | KakaoClient.KakaoAuthenticationException exception) {
      throw new PickUpException(KAKAO_AUTHENTICATION_FAILED);
    }
    Member member = kakaoMemberService.findOrCreate(kakaoUser);
    return authService.issueLogin(member);
  }
}
