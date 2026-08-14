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
  void 탈퇴한_카카오_회원과_같은_계정으로_다시_로그인하면_새_회원을_생성한다() {
    // Member.withdraw()가 oauthProvider/oauthSubject를 비우므로, 실제 DB에서는 탈퇴한 회원을
    // 더 이상 찾지 못하고(=findByOauthProviderAndOauthSubject가 empty) 신규 가입 경로를 탄다.
    KakaoClient.KakaoUser user = new KakaoClient.KakaoUser("kakao-subject", "profile.png");
    Member newMember = createMember("새로가입한닉네임", 20L);
    given(memberRepository.findByOauthProviderAndOauthSubject("KAKAO", user.subject()))
        .willReturn(Optional.empty());
    given(memberRepository.existsByNickname(anyString())).willReturn(false);
    given(memberRepository.save(any(Member.class))).willReturn(newMember);

    KakaoMemberService.KakaoMemberResult result = kakaoMemberService.findOrCreate(user);

    assertThat(result.created()).isTrue();
    assertThat(result.member()).isEqualTo(newMember);
    assertThat(result.member().getMemberId()).isNotEqualTo(1L);
  }

  @Test
  void 저장_중_유니크_제약이_위반되면_같은_트랜잭션_안에서_재시도하지_않고_즉시_전파한다() {
    // findOrCreate 는 @Transactional 이므로, 여기서 재시도하면 Spring 이 이미 rollback-only 로
    // 표시한 트랜잭션 위에서 재시도하는 셈이 되어 커밋 시점에 UnexpectedRollbackException 으로
    // 조용히 무효화된다. 그래서 이 메서드는 한 번만 시도하고 실패를 그대로 호출자에게 전파해야 하며,
    // 재시도는 트랜잭션 경계 밖인 KakaoAuthService 에서 새 트랜잭션으로 수행한다.
    KakaoClient.KakaoUser user = new KakaoClient.KakaoUser("kakao-subject", null);
    given(memberRepository.findByOauthProviderAndOauthSubject("KAKAO", user.subject()))
        .willReturn(Optional.empty());
    given(memberRepository.existsByNickname(anyString())).willReturn(false);
    given(memberRepository.save(any(Member.class)))
        .willThrow(new DataIntegrityViolationException("유니크 제약 충돌"));

    assertThatThrownBy(() -> kakaoMemberService.findOrCreate(user))
        .isInstanceOf(DataIntegrityViolationException.class);

    then(memberRepository).should(times(1)).save(any(Member.class));
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
