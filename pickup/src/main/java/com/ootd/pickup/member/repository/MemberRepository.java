package com.ootd.pickup.member.repository;

import java.util.Optional;

import com.ootd.pickup.member.domain.Member;

public interface MemberRepository {
    Optional<Member> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    Member save(Member member);
}
