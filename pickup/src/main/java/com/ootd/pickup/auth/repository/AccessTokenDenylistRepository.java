package com.ootd.pickup.auth.repository;

import java.time.Duration;

/** 탈퇴 등으로 즉시 세션을 끊어야 하는 회원의 액세스 토큰을 남은 만료 시간 동안 거부 목록에 올린다. */
public interface AccessTokenDenylistRepository {

  void denylistMember(Long memberId, Duration ttl);

  boolean isDenylisted(Long memberId);
}
