package com.ootd.pickup.auth.service;

import static com.ootd.pickup.global.exception.ExceptionCode.KAKAO_AUTHENTICATION_FAILED;

import com.ootd.pickup.auth.dto.KakaoLoginRequest;
import com.ootd.pickup.auth.kakao.KakaoClient;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class KakaoAuthService {
  private static final String PROVIDER = "KAKAO";
  private final KakaoClient kakaoClient;
  private final MemberRepository memberRepository;
  private final PointRepository pointRepository;
  private final AuthService authService;

  @Transactional
  public LoginResponse login(KakaoLoginRequest request) {
    KakaoClient.KakaoUser kakaoUser;
    try {
      kakaoUser = kakaoClient.authenticate(request);
    } catch (RestClientException | NullPointerException exception) {
      throw new PickUpException(KAKAO_AUTHENTICATION_FAILED);
    }
    Member member =
        memberRepository
            .findByOauthProviderAndOauthSubject(PROVIDER, kakaoUser.subject())
            .orElseGet(() -> createMember(kakaoUser));
    return authService.issueLogin(member);
  }

  private Member createMember(KakaoClient.KakaoUser user) {
    String nickname = "kakao_" + user.subject();
    Member member =
        memberRepository.save(
            Member.createOAuth(PROVIDER, user.subject(), nickname, user.profileImageUrl()));
    pointRepository.save(Point.create(member.getMemberId()));
    return member;
  }
}
