package com.ootd.pickup.member.repository;

import com.ootd.pickup.member.domain.Member;
import java.util.Optional;

public interface MemberRepository {
  Optional<Member> findByLoginId(String loginId);

  boolean existsByLoginId(String loginId);

  boolean existsByNickname(String nickname);

  Member save(Member member);
}
