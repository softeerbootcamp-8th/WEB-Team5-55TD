package com.ootd.pickup.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auth.dto.KakaoLoginRequest;
import com.ootd.pickup.auth.kakao.KakaoClient;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import com.ootd.pickup.point.repository.PointJpaRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class KakaoAuthServiceConcurrencyTest {

  @Autowired private KakaoAuthService kakaoAuthService;

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private PointJpaRepository pointJpaRepository;

  @MockitoBean private KakaoClient kakaoClient;

  @AfterEach
  void tearDown() {
    pointJpaRepository.deleteAll();
    memberJpaRepository.deleteAll();
  }

  @Test
  void 같은_카카오_계정으로_동시에_로그인해도_회원이_한_번만_생성된다() throws Exception {
    // given
    KakaoClient.KakaoUser kakaoUser = new KakaoClient.KakaoUser("same-subject", null);
    given(kakaoClient.authenticate(any())).willReturn(kakaoUser);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    // when
    List<LoginResponse> results;
    try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
      Future<LoginResponse> first = executorService.submit(() -> loginAfterSignal(ready, start));
      Future<LoginResponse> second = executorService.submit(() -> loginAfterSignal(ready, start));
      ready.await();
      start.countDown();
      results = List.of(first.get(), second.get());
    }

    // then
    List<Member> members =
        memberJpaRepository.findByOauthProviderAndOauthSubject("KAKAO", "same-subject").stream()
            .toList();
    assertThat(members).hasSize(1);
    Long memberId = members.get(0).getMemberId();
    assertThat(results).extracting(response -> response.body().memberId()).containsOnly(memberId);
    assertThat(pointJpaRepository.findByMemberId(memberId)).isPresent();
  }

  private LoginResponse loginAfterSignal(CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    return kakaoAuthService.login(
        new KakaoLoginRequest("auth-code", "https://pickup.test/callback"));
  }
}
