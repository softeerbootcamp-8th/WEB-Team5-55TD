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

  @Transactional
  public KakaoMemberResult findOrCreate(KakaoClient.KakaoUser user) {
    return memberRepository
        .findByOauthProviderAndOauthSubject(PROVIDER, user.subject())
        .map(member -> new KakaoMemberResult(member, false))
        .orElseGet(() -> new KakaoMemberResult(createMember(user), true));
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
