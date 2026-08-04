package com.ootd.pickup.member.repository;

import com.ootd.pickup.member.domain.Member;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class MemberDataJpaRepository implements MemberRepository {

  private final MemberJpaRepository memberJpaRepository;

  @Override
  public Optional<Member> findByLoginId(String loginId) {
    return memberJpaRepository.findByLoginId(loginId);
  }

  @Override
  public boolean existsByLoginId(String loginId) {
    return memberJpaRepository.existsByLoginId(loginId);
  }

  @Override
  public boolean existsByNickname(String nickname) {
    return memberJpaRepository.existsByNickname(nickname);
  }

  @Override
  public Optional<Member> findById(Long memberId) {
    return memberJpaRepository.findById(memberId);
  }

  @Override
  public Page<Member> searchMembers(String q, Pageable pageable) {
    String keyword = StringUtils.hasText(q) ? q : "";
    return memberJpaRepository.findByLoginIdContainingOrNicknameContaining(
        keyword, keyword, pageable);
  }

  @Override
  public Member save(Member member) {
    return memberJpaRepository.save(member);
  }
}
