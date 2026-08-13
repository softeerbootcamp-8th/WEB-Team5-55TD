package com.ootd.pickup.auth.service;

import static com.ootd.pickup.global.exception.ExceptionCode.KAKAO_AUTHENTICATION_FAILED;

import com.ootd.pickup.auth.dto.KakaoLoginRequest;
import com.ootd.pickup.auth.kakao.KakaoClient;
import com.ootd.pickup.global.exception.PickUpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@Slf4j
@RequiredArgsConstructor
public class KakaoAuthService {
  private static final int MAX_FIND_OR_CREATE_ATTEMPTS = 3;

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
    KakaoMemberService.KakaoMemberResult result = findOrCreateWithRetry(kakaoUser);
    return authService.issueLogin(result.member(), result.created());
  }

  /**
   * 동시 가입으로 랜덤 닉네임 또는 카카오 계정이 충돌하면 findOrCreate 가 DataIntegrityViolationException 을 던진다. 매 시도마다
   * kakaoMemberService.findOrCreate 를 새로 호출해 새 트랜잭션에서 처리한다 — 실패한 트랜잭션 안에서 재시도하면 Spring 이 이미
   * rollback-only 로 표시해 두어 커밋 시 조용히 무효화되므로, 반드시 트랜잭션 경계 밖(다른 빈에서 프록시를 다시 태우는 방식)에서 재시도해야 한다.
   */
  private KakaoMemberService.KakaoMemberResult findOrCreateWithRetry(
      KakaoClient.KakaoUser kakaoUser) {
    for (int attempt = 1; attempt <= MAX_FIND_OR_CREATE_ATTEMPTS; attempt++) {
      try {
        return kakaoMemberService.findOrCreate(kakaoUser);
      } catch (DataIntegrityViolationException exception) {
        if (attempt == MAX_FIND_OR_CREATE_ATTEMPTS) {
          throw exception;
        }
        log.warn("카카오 회원 조회/생성 충돌로 재시도합니다 - subject={}, attempt={}", kakaoUser.subject(), attempt);
      }
    }
    throw new IllegalStateException("도달할 수 없는 코드입니다.");
  }
}
