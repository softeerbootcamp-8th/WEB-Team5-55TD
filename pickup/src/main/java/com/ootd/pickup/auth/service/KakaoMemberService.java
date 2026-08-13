package com.ootd.pickup.auth.service;

import com.ootd.pickup.auth.kakao.KakaoClient;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class KakaoMemberService {
  private static final String PROVIDER = "KAKAO";
  private static final int MAX_CREATE_ATTEMPTS = 3;

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
    for (int attempt = 1; attempt <= MAX_CREATE_ATTEMPTS; attempt++) {
      String nickname = RandomNicknameGenerator.generate(memberRepository::existsByNickname);
      try {
        Member member =
            memberRepository.save(
                Member.createOAuth(PROVIDER, user.subject(), nickname, user.profileImageUrl()));
        pointRepository.save(Point.create(member.getMemberId()));
        return member;
      } catch (DataIntegrityViolationException exception) {
        if (attempt == MAX_CREATE_ATTEMPTS) {
          throw exception;
        }
        log.warn("카카오 회원 생성 중 닉네임 선점 충돌로 재시도합니다 - subject={}, attempt={}", user.subject(), attempt);
      }
    }
    throw new IllegalStateException("도달할 수 없는 코드입니다.");
  }

  public record KakaoMemberResult(Member member, boolean created) {}
}
