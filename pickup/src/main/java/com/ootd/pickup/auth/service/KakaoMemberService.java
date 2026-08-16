package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.kakao.KakaoClient;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoMemberService {
  private static final String PROVIDER = "KAKAO";

  private final MemberRepository memberRepository;
  private final PointRepository pointRepository;

  /**
   * 동시에 같은 랜덤 닉네임 또는 같은 카카오 계정으로 가입이 몰리면 유니크 제약 위반으로 실패할 수 있다. 이 메서드는 단일 트랜잭션에서 한 번만 시도한다 — 실패 시 이
   * 트랜잭션 안에서 재시도하지 않는다(Spring이 첫 실패 시점에 트랜잭션을 rollback-only로 표시해, 같은 트랜잭션 안의 재시도는 커밋 시점에
   * UnexpectedRollbackException으로 조용히 무효화되기 때문). 재시도가 필요하면 호출자(KakaoAuthService)가 이 메서드를 새 트랜잭션으로
   * 다시 호출해야 한다.
   */
  @Transactional
  public KakaoMemberResult findOrCreate(KakaoClient.KakaoUser user) {
    return memberRepository
        .findByOauthProviderAndOauthSubject(PROVIDER, user.subject())
        .map(member -> existingMemberResult(member, user))
        .orElseGet(() -> new KakaoMemberResult(createMember(user), true));
  }

  private KakaoMemberResult existingMemberResult(Member member, KakaoClient.KakaoUser user) {
    if (member.isWithdrawn()) {
      // 배포 전에 탈퇴해 OAuth 정보가 남은 회원도 재가입할 수 있도록 연결을 정리한다.
      member.clearOAuthIdentity();
      memberRepository.flush();
      return new KakaoMemberResult(createMember(user), true);
    }
    return new KakaoMemberResult(member, false);
  }

  private Member createMember(KakaoClient.KakaoUser user) {
    String nickname = RandomNicknameGenerator.generate(memberRepository::existsByNickname);
    Member member =
        memberRepository.save(
            Member.createOAuth(PROVIDER, user.subject(), nickname, user.profileImageUrl()));
    pointRepository.save(Point.create(member.getMemberId()));
    return member;
  }

  public record KakaoMemberResult(Member member, boolean created) {}
}
