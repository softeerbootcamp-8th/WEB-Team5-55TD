package com.ootd.pickup.member.repository;

import com.ootd.pickup.member.domain.Member;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberRepository {
  Optional<Member> findByLoginId(String loginId);

  boolean existsByLoginId(String loginId);

  boolean existsByNickname(String nickname);

  boolean existsById(Long memberId);

  Optional<Member> findById(Long memberId);

  Page<Member> searchMembers(String q, Pageable pageable);

  Member save(Member member);
}
