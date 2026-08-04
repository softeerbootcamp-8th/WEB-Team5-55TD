package com.ootd.pickup.member.repository;

import com.ootd.pickup.member.domain.Member;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByLoginId(String loginId);

  boolean existsByLoginId(String loginId);

  boolean existsByNickname(String nickname);

  Page<Member> findByLoginIdContainingOrNicknameContaining(
      String loginIdPart, String nicknamePart, Pageable pageable);
}
